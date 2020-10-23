package xyz.leutgeb.lorenz.lac.typing.resources.solving;

import static com.microsoft.z3.Status.SATISFIABLE;
import static com.microsoft.z3.Status.UNKNOWN;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static xyz.leutgeb.lorenz.lac.util.Util.bug;
import static xyz.leutgeb.lorenz.lac.util.Util.output;
import static xyz.leutgeb.lorenz.lac.util.Util.randomHex;
import static xyz.leutgeb.lorenz.lac.util.Util.signum;
import static xyz.leutgeb.lorenz.lac.util.Z3Support.load;

import com.google.common.collect.HashBiMap;
import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Model;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.RatNum;
import com.microsoft.z3.Statistics;
import com.microsoft.z3.Status;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.Coefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.KnownCoefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.UnknownCoefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.constraints.Constraint;
import xyz.leutgeb.lorenz.lac.util.Fraction;
import xyz.leutgeb.lorenz.lac.util.Util;

@Slf4j
public class ConstraintSystemSolver {
  private static Map<String, String> z3Config(boolean unsatCore) {
    // Execute `z3 -p` to get a list of parameters.
    return Map.of("unsat_core", String.valueOf(unsatCore));
  }

  public static Optional<Map<Coefficient, KnownCoefficient>> solve(Set<Constraint> constraints) {
    return solve(constraints, Paths.get("out", randomHex()), emptyList(), Domain.INTEGER);
  }

  public static Optional<Map<Coefficient, KnownCoefficient>> solve(
      Set<Constraint> constraints, Path outPath) {
    return solve(constraints, outPath, emptyList(), Domain.INTEGER);
  }

  public static Optional<Map<Coefficient, KnownCoefficient>> solve(
      Set<Constraint> constraints, Path outPath, List<UnknownCoefficient> target) {
    return solve(constraints, outPath, target, Domain.INTEGER);
  }

  public static Optional<Map<Coefficient, KnownCoefficient>> solve(
      Set<Constraint> constraints, Path outPath, List<UnknownCoefficient> target, Domain domain) {
    load();

    final var unsatCore = target.isEmpty();

    try (final var ctx = new Context(z3Config(unsatCore))) {
      final var solver = ctx.mkSolver();

      var optimize = !target.isEmpty();
      final Optimize opt = optimize ? ctx.mkOptimize() : null;

      final var generatedCoefficients = HashBiMap.<ArithExpr, UnknownCoefficient>create();

      final var coefficients = new HashSet<Coefficient>();
      for (Constraint constraint : constraints) {
        coefficients.addAll(constraint.occurringCoefficients());
      }

      for (var coefficient : coefficients) {
        if (!(coefficient instanceof UnknownCoefficient)) {
          continue;
        }
        final var unknownCoefficient = (UnknownCoefficient) coefficient;
        if (generatedCoefficients.inverse().containsKey(coefficient)) {
          continue;
        }
        if (!generatedCoefficients.inverse().containsKey(coefficient)) {
          final var it =
              Domain.INTEGER.equals(domain)
                  ? ctx.mkIntConst(unknownCoefficient.getName())
                  : ctx.mkRealConst(unknownCoefficient.getName());
          if (!unknownCoefficient.isMaybeNegative()) {
            final var positive = ctx.mkGe(it, ctx.mkInt(0));
            if (optimize) {
              opt.Add(positive);
            } else {
              if (unsatCore && false) {
                solver.assertAndTrack(
                    positive, ctx.mkBoolConst("non negative " + unknownCoefficient));
              } else {
                solver.add(positive);
              }
            }
          }
          generatedCoefficients.inverse().put((UnknownCoefficient) coefficient, it);
        }
      }

      if (optimize) {
        target.forEach(
            x -> {
              if (!generatedCoefficients.inverse().containsKey(x)) {
                log.warn("Could not find generated coefficient for optimization target '{}'", x);
              } else {
                opt.MkMinimize(generatedCoefficients.inverse().get(x));
              }
            });
      }

      for (Constraint c : constraints) {
        if (optimize) {
          opt.Add(c.encode(ctx, generatedCoefficients.inverse()));
        } else {
          if (unsatCore) {
            solver.assertAndTrack(
                c.encode(ctx, generatedCoefficients.inverse()), ctx.mkBoolConst(c.getTracking()));
          } else {
            solver.add(c.encode(ctx, generatedCoefficients.inverse()));
          }
        }
      }

      log.trace("lac Coefficients: " + generatedCoefficients.keySet().size());
      log.trace("lac Constraints:  " + constraints.size());
      log.trace("Z3  Scopes:       " + (optimize ? "?" : solver.getNumScopes()));
      log.trace("Z3  Assertions:   " + (optimize ? "?" : solver.getAssertions().length));

      final Path smtFile = outPath.resolve("instance.smt");

      try (final var out = new PrintWriter(output(smtFile))) {
        out.println(optimize ? opt : solver);
        log.debug("Wrote SMT instance to {}.", smtFile);
      } catch (IOException ioException) {
        log.warn("Failed to write SMT instance to {}.", smtFile, ioException);
        ioException.printStackTrace();
      }

      Optional<Model> optionalModel = Optional.empty();

      if (optimize) {
        optionalModel =
            check(
                opt::Check,
                opt::getModel,
                opt::getUnsatCore,
                unsatCore,
                opt::toString,
                opt::getStatistics,
                outPath);
      } else {
        optionalModel =
            check(
                solver::check,
                solver::getModel,
                solver::getUnsatCore,
                unsatCore,
                solver::toString,
                solver::getStatistics,
                outPath);
      }
      if (optionalModel.isEmpty()) {
        return empty();
      }
      final Model model = optionalModel.get();
      final var solution = new HashMap<Coefficient, KnownCoefficient>();
      for (final var e : generatedCoefficients.entrySet()) {
        var x = model.getConstInterp(e.getKey());
        if (Domain.RATIONAL.equals(domain) && !x.isRatNum()) {
          log.warn("solution for " + e.getValue() + " is not a rational number, it is " + x);
        }
        KnownCoefficient v;
        if (x instanceof RatNum && Domain.RATIONAL.equals(domain)) {
          final var xr = (RatNum) x;
          var num = xr.getNumerator();
          if (num.getBigInteger().intValueExact() == 0) {
            v = KnownCoefficient.ZERO;
          } else {
            v = new KnownCoefficient(Util.toFraction(xr));
          }
        } else if (x instanceof IntNum && Domain.INTEGER.equals(domain)) {
          final var xr = (IntNum) x;
          if (xr.getBigInteger().intValueExact() == 0) {
            v = KnownCoefficient.ZERO;
          } else {
            v = new KnownCoefficient(new Fraction(xr.getInt()));
          }
        } else {
          throw bug("interpretation contains constant of unknown or unexpected type");
        }
        if (signum(v.getValue()) < 0 && !e.getValue().isMaybeNegative()) {
          log.warn("Got negative coefficient");
        }
        solution.put(e.getValue(), v);
      }

      if (solution.size() != generatedCoefficients.size()) {
        log.warn("Partial solution!");
      }

      return Optional.of(solution);
    }
  }

  private static Optional<Model> check(
      Supplier<Status> check,
      Supplier<Model> getModel,
      Supplier<BoolExpr[]> getUnsatCore,
      boolean unsatCore,
      Supplier<String> program,
      Supplier<Statistics> statistics,
      Path outPath) {
    final var start = Instant.now();
    log.debug("Solving start: " + start);
    var status = check.get();
    final var stop = Instant.now();
    log.debug("Solving duration: " + (Duration.between(start, stop)));
    if (SATISFIABLE.equals(status)) {
      try {
        try (final var out = output(outPath.resolve("z3-statistics.txt"))) {
          final var printer = new PrintStream(out);
          for (var entry : statistics.get().getEntries()) {
            log.trace("{}={}", entry.Key, entry.getValueString());
            printer.println(entry.Key + "=" + entry.getValueString());
          }
        }
      } catch (Exception exception) {
        // ignored
      }
      return Optional.of(getModel.get());
    } else if (UNKNOWN.equals(status)) {
      log.error("Attempt to solve constraint system yielded unknown result.");
      throw new RuntimeException(new TimeoutException("satisfiability of constraints unknown"));
    }
    log.error("Constraint system is unsatisfiable!");
    if (!unsatCore) {
      log.error("Got no unsat core");
      return Optional.empty();
    }
    Set<String> coreSet =
        Arrays.stream(getUnsatCore.get())
            .map(Object::toString)
            .map(x -> x.substring(1, x.length() - 1))
            .collect(Collectors.toSet());

    log.info(
        "Unsatisfiable core (raw from Z3):\n{}",
        Stream.of(program.get().replaceAll("\\n\\s+", " ").split("\n"))
            .filter(line -> line.contains("assert"))
            .filter(line -> coreSet.stream().anyMatch(line::contains))
            .collect(Collectors.joining("\n")));

    return empty();
  }

  public enum Domain {
    RATIONAL,
    INTEGER
  }
}
