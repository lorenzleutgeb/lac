package xyz.leutgeb.lorenz.lac;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import xyz.leutgeb.lorenz.lac.typing.resources.Annotation;
import xyz.leutgeb.lorenz.lac.typing.resources.constraints.Constraint;
import xyz.leutgeb.lorenz.lac.typing.resources.optimiziation.Optimization;
import xyz.leutgeb.lorenz.lac.typing.simple.TypeError;
import xyz.leutgeb.lorenz.lac.unification.UnificationError;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.leutgeb.lorenz.lac.TestUtil.TACTICS;
import static xyz.leutgeb.lorenz.lac.typing.resources.Annotation.unitIndex;
import static xyz.leutgeb.lorenz.lac.typing.resources.coefficients.KnownCoefficient.ONE;
import static xyz.leutgeb.lorenz.lac.typing.resources.coefficients.KnownCoefficient.ONE_BY_TWO;

public class EvalTable {
  static final Annotation Qp = new Annotation(List.of(ONE_BY_TWO), Map.of(unitIndex(1), ONE), "Q'");

  private static Stream<Arguments> table() {
    return Stream.of(
        // Arguments.of("SplayTree.splay_zigzig", false, false),
        // Arguments.of("SplayTree.splay_zigzig", true, false),
        // Arguments.of("SplayTree.splay_zigzig", false, false),

        // Generation: automatic (improved), Weakening: selective
        Arguments.of("PairingHeap.merge_pairs_isolated", false, false),
        Arguments.of("SplayHeap.partition", false, false),
        Arguments.of("SplayTree.splay", false, false),

        // Generation: manual, Weakening: selective
        Arguments.of("PairingHeap.merge_pairs_isolated", true, false),
        Arguments.of("SplayHeap.partition", true, false),
        Arguments.of("SplayTree.splay", true, false),

        // Generation: automatic (naive), Weakening: all
        Arguments.of("PairingHeap.merge_pairs_isolated", false, true),
        Arguments.of("SplayHeap.partition", false, true),
        Arguments.of("SplayTree.splay", false, true));
  }

  @ParameterizedTest
  @MethodSource({"table"})
  public void test(String fqn, boolean useTactics, boolean naive)
      throws UnificationError, TypeError, IOException {

    /*
    System.setProperty(
        "xyz.leutgeb.lorenz.lac.typing.resources.proving.Prover.naive", String.valueOf(naive));

    System.setProperty(
        "com.microsoft.z3.timeout", String.valueOf(Duration.of(30, ChronoUnit.MINUTES).toMillis()));
     */

    System.out.println("tactics = " + useTactics);
    System.out.println("naive = " + naive);

    final var program = TestUtil.loadAndNormalizeAndInferAndUnshare(singleton(fqn));

    final var constraints = new HashSet<Constraint>();
    // constraints.addAll(program.rightSide(Qp));
    /*
    constraints.addAll(
        Optimization.forceRank(
            program
                .getFunctionDefinitions()
                .get(fqn)
                .getInferredSignature()
                .getAnnotation()
                .get()
                .withCost));
     */

    //final var target = Optimization.standard(program);
    //constraints.addAll(target.constraints);

    final var result =
        program.solve(
            new HashMap<>(),
            useTactics ? program.lookupTactics(emptyMap(), TACTICS) : emptyMap(),
            true,
            constraints);

    assertTrue(result.isSatisfiable());
    program.printAllInferredSignaturesInOrder(System.out);
  }
}
