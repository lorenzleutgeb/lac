package xyz.leutgeb.lorenz.lac;

import static java.util.Collections.*;
import static org.hipparchus.fraction.Fraction.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static xyz.leutgeb.lorenz.lac.Util.fqnToFlatFilename;
import static xyz.leutgeb.lorenz.lac.typing.resources.Annotation.unitIndex;
import static xyz.leutgeb.lorenz.lac.typing.resources.coefficients.KnownCoefficient.ZERO;
import static xyz.leutgeb.lorenz.lac.typing.simple.TypeConstraint.eq;
import static xyz.leutgeb.lorenz.lac.typing.simple.TypeConstraint.ord;
import static xyz.leutgeb.lorenz.lac.typing.simple.TypeVariable.ALPHA;
import static xyz.leutgeb.lorenz.lac.typing.simple.TypeVariable.BETA;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hipparchus.fraction.Fraction;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import xyz.leutgeb.lorenz.lac.ast.Identifier;
import xyz.leutgeb.lorenz.lac.ast.Program;
import xyz.leutgeb.lorenz.lac.typing.resources.Annotation;
import xyz.leutgeb.lorenz.lac.typing.resources.FunctionAnnotation;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.Coefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.KnownCoefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.UnknownCoefficient;
import xyz.leutgeb.lorenz.lac.typing.resources.constraints.InequalityConstraint;
import xyz.leutgeb.lorenz.lac.typing.resources.constraints.LessThanOrEqualConstraint;
import xyz.leutgeb.lorenz.lac.typing.resources.constraints.OffsetConstraint;
import xyz.leutgeb.lorenz.lac.typing.simple.FunctionSignature;
import xyz.leutgeb.lorenz.lac.typing.simple.TypeConstraint;
import xyz.leutgeb.lorenz.lac.typing.simple.TypeError;
import xyz.leutgeb.lorenz.lac.typing.simple.types.BoolType;
import xyz.leutgeb.lorenz.lac.typing.simple.types.TreeType;
import xyz.leutgeb.lorenz.lac.typing.simple.types.Type;
import xyz.leutgeb.lorenz.lac.unification.UnificationError;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public class Tests {
  static final TreeType ATREE = new TreeType(ALPHA);
  private static final TreeType BTREE = new TreeType(BETA);
  private static final BoolType BOOL = BoolType.INSTANCE;
  private static final File OUT = new File("out");

  public static Loader loader() {
    return new Loader(Path.of(".", "src", "test", "resources", "examples"));
  }

  private static Stream<Arguments> nonConstantCostDefinitions() {
    return Stream.of(
        arguments("LeftList.postorder", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("LeftList.rev_append", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("LeftList.append", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("RightList.append", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("RightList.rev_append", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("LeftList.inorder", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("LeftList.preorder", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments(
            "Tree.contains_unordered",
            sig(singleton(eq(ALPHA)), ALPHA, ATREE, BOOL),
            ExpectedResult.UNKNOWN),
        arguments("LeftList.postorder", sig(ATREE, ATREE, ATREE), ExpectedResult.UNKNOWN),
        arguments("SplayTree.splay_max", sig(ATREE, ATREE), ExpectedResult.SAT),
        arguments(
            "SplayTree.splay", sig(Set.of(ord(ALPHA)), ALPHA, ATREE, ATREE), ExpectedResult.SAT),
        arguments(
            "SplayTree.delete",
            sig(Set.of(ord(ALPHA)), ALPHA, ATREE, ATREE),
            ExpectedResult.UNKNOWN),
        arguments(
            "SkewHeap.merge",
            sig(singleton(ord(ALPHA)), ATREE, ATREE, ATREE),
            ExpectedResult.UNKNOWN),
        arguments(
            "SkewHeap.insert",
            sig(singleton(ord(ALPHA)), ALPHA, ATREE, ATREE),
            ExpectedResult.UNKNOWN),
        arguments("PairingHeap.del_min", sig(singleton(ord(ALPHA)), ATREE, ATREE)),
        arguments("PairingHeap.merge_pairs", sig(singleton(ord(ALPHA)), ATREE, ATREE)));
  }

  private static Stream<Arguments> constantCostDefinitions() {
    return Stream.of(
        arguments("LeftList.cons", sig(ALPHA, ATREE, ATREE), 0),
        arguments("RightList.cons", sig(ALPHA, ATREE, ATREE), 0),
        arguments("Tree.singleton", sig(ALPHA, ATREE), 0),
        arguments("Bool.neg", sig(BOOL, BOOL), 0),
        arguments("Bool.or", sig(BOOL, BOOL, BOOL), 0),
        arguments("Bool.and", sig(BOOL, BOOL, BOOL), 0),
        arguments("RightList.tl", sig(ATREE, ATREE), 0),
        arguments("LeftList.tl", sig(ATREE, ATREE), 0),
        arguments("Tree.id", sig(ATREE, ATREE), 0),
        arguments("Tree.right", sig(ATREE, ATREE), 0),
        arguments("Tree.left", sig(ATREE, ATREE), 0),
        arguments("Tree.flip", sig(ATREE, ATREE), 0),
        arguments("Tree.empty", sig(ATREE, BOOL), 0),
        arguments("Tree.clone", sig(ALPHA, ATREE, ATREE), 0),
        arguments("PairingHeap.is_root", sig(ATREE, BOOL), 0),
        arguments("PairingHeap.link", sig(singleton(ord(ALPHA)), ATREE, ATREE), 0),
        arguments("PairingHeap.merge", sig(singleton(ord(ALPHA)), ATREE, ATREE, ATREE), 1),
        arguments("PairingHeap.insert", sig(singleton(ord(ALPHA)), ALPHA, ATREE, ATREE), 3),
        arguments("Scratch.empty_1", sig(ATREE, BOOL), 1),
        arguments("Scratch.empty_2", sig(ATREE, BOOL), 2),
        arguments("Scratch.id", sig(ALPHA, ALPHA), 0),
        arguments("Scratch.left", sig(ALPHA, BETA, ALPHA), 0),
        arguments("Scratch.right", sig(ALPHA, BETA, BETA), 0),
        arguments("Scratch.empty_3", sig(ATREE, BOOL), 1),
        arguments("Scratch.empty_4", sig(ATREE, BTREE, BOOL), 3),
        arguments("Scratch.first_nonempty_and_second_empty", sig(ATREE, BTREE, BOOL), 1));
  }

  private static FunctionSignature sig(Set<TypeConstraint> constraints, Type... types) {
    return new FunctionSignature(constraints, types);
  }

  private static FunctionSignature sig(Type... types) {
    return new FunctionSignature(emptySet(), types);
  }

  private static Stream<Arguments> infiniteCostDefinitions() {
    return Stream.of(
        arguments("Infinite.infinite_1"),
        arguments("Infinite.infinite_2"),
        arguments("Infinite.infinite_3"),
        arguments("Infinite.infinite_4"),
        arguments("Infinite.infinite_5"),
        arguments("Infinite.infinite_6"),
        arguments("Infinite.infinite_7"),
        arguments("Infinite.infinite_8"),
        arguments("Infinite.infinite_9"),
        arguments("Infinite.infinite_10"),
        arguments("Infinite.infinite_11"),
        arguments("Infinite.infinite_12"),
        arguments("Infinite.infinite_13"),
        arguments("Infinite.infinite_14"),
        arguments("Infinite.infinite_15"),
        arguments("Infinite.infinite_16"),
        arguments("Infinite.infinite_17"),
        arguments("Infinite.infinite_18"),
        arguments("Infinite.infinite_19a"),
        arguments("Infinite.infinite_19b"));
  }

  static NidiExporter<Identifier, SizeEdge> exporter() {
    final var exporter =
        new NidiExporter<Identifier, SizeEdge>(
            identifier ->
                identifier.getName()
                    + "_"
                    + (identifier.getIntro() == null
                        ? "null"
                        : (identifier.getIntro().getFqn()
                            + "_"
                            + Util.stamp(identifier.getIntro().getExpression()))));
    exporter.setVertexAttributeProvider(
        v -> {
          return Map.of("label", new DefaultAttribute<>(v.getName(), AttributeType.STRING));
        });
    exporter.setEdgeAttributeProvider(
        e -> {
          return Map.of(
              // "label",
              // new DefaultAttribute<>(e.getKind().toString(), AttributeType.STRING),
              "color",
              new DefaultAttribute<>(
                  e.getKind().equals(SizeEdge.Kind.EQ) ? "blue4" : "red", AttributeType.STRING));
        });
    return exporter;
  }

  private Program loadAndNormalizeAndInferAndUnshare(String fqn)
      throws UnificationError, TypeError, IOException {
    final var result = loader().load(fqn);
    result.normalize();
    result.infer();
    result.unshare();
    return result;
  }

  @ParameterizedTest
  @MethodSource("nonConstantCostDefinitions")
  void nonConstantCost(String fqn, FunctionSignature expectedSignature) throws Exception {
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    for (var e : program.getFunctionDefinitions().values()) {
      e.printHaskellTo(new PrintStream(new File(OUT, e.getName() + ".hs").getAbsoluteFile()));
    }

    final var definition = program.getFunctionDefinitions().get(fqn);

    assertNotNull(definition);
    assertEquals(expectedSignature, definition.getAnnotatedSignature(), "annotated signature");
    assertEquals(expectedSignature, definition.getInferredSignature(), "inferred signature");

    System.out.println("Testing " + fqn);

    // TODO: Check outcome.
    program.solve();
  }

  @Test
  @Disabled("too complex, see separate test classes")
  void splay() throws Exception {
    final String fqn = "SplayTree.splay";
    final var expectedSignature = sig(singleton(ord(ALPHA)), ALPHA, ATREE, ATREE);
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    final var definition = program.getFunctionDefinitions().get(fqn);

    assertNotNull(definition);
    assertEquals(expectedSignature, definition.getInferredSignature());

    // Taken from Example 8.
    final List<Coefficient> rankCoefficients = new ArrayList<>(1);
    rankCoefficients.add(new KnownCoefficient(ONE));

    final Map<List<Integer>, Coefficient> schoenmakers = new HashMap<>();
    schoenmakers.put(List.of(0, 1), new KnownCoefficient(new Fraction(3, 1)));
    schoenmakers.put(List.of(0, 2), new KnownCoefficient(ONE));

    final List<Coefficient> resultRankCoefficients = new ArrayList<>(1);
    resultRankCoefficients.add(new KnownCoefficient(ONE));

    final var predefinedAnnotations = new HashMap<String, FunctionAnnotation>();
    predefinedAnnotations.put(
        fqn,
        new FunctionAnnotation(
            new Annotation(rankCoefficients, schoenmakers, "predefined"),
            new Annotation(resultRankCoefficients, emptyMap(), "predefined")));

    assertTrue(program.solve(predefinedAnnotations).isPresent());
  }

  @ParameterizedTest
  @MethodSource("infiniteCostDefinitions")
  void infiniteCost(String fqn) throws Exception {
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    System.out.println("Testing " + fqn);
    final var solution = program.solve();
    assertTrue(solution.isEmpty());
  }

  @Test
  void revAppend() throws Exception {
    final var fqn = "LeftList.rev_append";
    final var expectedSignature = sig(ATREE, ATREE, ATREE);
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    final var definition = program.getFunctionDefinitions().get(fqn);

    System.out.println("Testing " + fqn);
    assertEquals(expectedSignature, definition.getInferredSignature());

    assertEquals(Optional.empty(), program.solve());
  }

  @Test
  @Disabled("too complex, see separate test classes")
  void splayZigZig() throws Exception {
    final var fqn = "SplayTree.splay_zigzig";
    final var expectedSignature = sig(ALPHA, ATREE, ATREE);
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    final var definition = program.getFunctionDefinitions().get(fqn);

    assertEquals(expectedSignature, definition.getInferredSignature());

    // Taken from Example 8.
    final List<Coefficient> rankCoefficients = new ArrayList<>(1);
    rankCoefficients.add(new KnownCoefficient(ONE));

    final Map<List<Integer>, Coefficient> schoenmakers = new HashMap<>();
    schoenmakers.put(List.of(0, 1), new KnownCoefficient(new Fraction(3, 1)));
    schoenmakers.put(List.of(0, 2), new KnownCoefficient(ONE));

    final List<Coefficient> resultRankCoefficients = new ArrayList<>(1);
    resultRankCoefficients.add(new KnownCoefficient(ONE));

    final var predefinedAnnotations = new HashMap<String, FunctionAnnotation>();
    predefinedAnnotations.put(
        fqn,
        new FunctionAnnotation(
            new Annotation(rankCoefficients, schoenmakers, "predefined"),
            new Annotation(resultRankCoefficients, emptyMap(), "predefined")));

    assertTrue(program.solve(predefinedAnnotations).isPresent());
  }

  @ParameterizedTest
  @MethodSource("constantCostDefinitions")
  void constantCost(final String fqn, FunctionSignature expectedSignature, int constantCost)
      throws Exception {
    final var program = loadAndNormalizeAndInferAndUnshare(fqn);
    final var definition = program.getFunctionDefinitions().get(fqn);

    assertNotNull(definition);
    System.out.println("Testing " + fqn);
    assertEquals(expectedSignature, definition.getInferredSignature());

    final var returnsTree = expectedSignature.getType().getTo() instanceof TreeType;

    final List<Coefficient> args =
        Stream.generate(() -> ZERO)
            .limit(expectedSignature.getType().getFrom().treeSize())
            .collect(Collectors.toList());

    // We show that it is possible to type the function in such a way that the difference between
    // the potential of the arguments and the potential of the result is exactly the cost that we
    // expect.
    final var tightInput = new UnknownCoefficient("tightInput");
    final var tightResult = new UnknownCoefficient("tightResult");
    final var tight = new HashMap<String, FunctionAnnotation>();
    tight.put(
        fqn,
        new FunctionAnnotation(
            Annotation.constant(
                (int) expectedSignature.getType().getFrom().treeSize(), "expectedArgs", tightInput),
            new Annotation(
                returnsTree ? singletonList(ZERO) : emptyList(),
                returnsTree ? Map.of(unitIndex(1), tightResult) : emptyMap(),
                "expectedReturn")));

    assertTrue(
        program
            .solve(
                tight,
                singleton(
                    new OffsetConstraint(
                        tightInput, tightResult, new Fraction(constantCost), "outside")))
            .isPresent());

    if (constantCost > 0) {
      // We show that it is impossible to type the function in such a way that the the potential of
      // the arguments is less than we expect.
      final var tooSmallInput = new UnknownCoefficient("tightInput");
      final var tooSmall = new HashMap<String, FunctionAnnotation>();
      final var costKnownCoefficient = new KnownCoefficient(new Fraction(constantCost - 1));
      tooSmall.put(
          fqn,
          new FunctionAnnotation(
              new Annotation(
                  args,
                  Map.of(
                      unitIndex((int) expectedSignature.getType().getFrom().treeSize()),
                      tooSmallInput),
                  "expectedArgs"),
              new Annotation(
                  returnsTree ? singletonList(ZERO) : emptyList(),
                  returnsTree ? Map.of(unitIndex(1), ZERO) : emptyMap(),
                  "expectedReturn")));

      assertTrue(
          program
              .solve(
                  tooSmall,
                  singleton(
                      new LessThanOrEqualConstraint(
                          tooSmallInput, costKnownCoefficient, "outside constraint")))
              .isEmpty(),
          "No solution is expected, since it should not be possible to type the program with a cost of "
              + (constantCost - 1));
    }

    if (!returnsTree) {
      return;
    }

    // We show that it is impossible to type the function in such a way that the the potential of
    // the arguments is less than the potential of the result.
    final var generatorInput = new UnknownCoefficient("generatorInput");
    final var generatorResult = new UnknownCoefficient("generatorResult");
    final var symbolicGenerator = new HashMap<String, FunctionAnnotation>();
    symbolicGenerator.put(
        fqn,
        new FunctionAnnotation(
            new Annotation(
                args,
                Map.of(
                    unitIndex((int) expectedSignature.getType().getFrom().treeSize()),
                    generatorInput),
                "expectedGeneratorArgs"),
            new Annotation(
                singletonList(ZERO),
                Map.of(unitIndex(1), generatorResult),
                "expectedGeneratorReturn")));

    assertEquals(
        Optional.empty(),
        program.solve(
            symbolicGenerator,
            Set.of(
                new LessThanOrEqualConstraint(
                    generatorInput, generatorResult, "outside constraint"),
                new InequalityConstraint(generatorInput, generatorResult, "outside constraint"))));
  }

  @Test
  void dumps() throws Exception {
    final var loader = loader();
    loader.autoload();
    final var program = loader.all();
    loader.exportGraph(new FileOutputStream(new File(OUT, "all.dot")));
    for (var fd : program.getFunctionDefinitions().values()) {
      fd.printTo(
          new PrintStream(
              new File(OUT, fqnToFlatFilename(fd.getFullyQualifiedName()) + "-loaded.ml")
                  .getAbsoluteFile()));
    }
    program.normalize();
    for (var fd : program.getFunctionDefinitions().values()) {
      fd.printTo(
          new PrintStream(
              new File(OUT, fqnToFlatFilename(fd.getFullyQualifiedName()) + "-normalized.ml")
                  .getAbsoluteFile()));
    }
    program.infer();
    program.unshare();
    for (var fd : program.getFunctionDefinitions().values()) {
      fd.printTo(
          new PrintStream(
              new File(OUT, fqnToFlatFilename(fd.getFullyQualifiedName()) + "-unshared.ml")
                  .getAbsoluteFile()));
    }

    final var exporter = exporter();

    for (var fd : program.getFunctionDefinitions().values()) {
      fd.analyzeSizes();
      final var exp = exporter.transform(fd.getSizeAnalysis());
      final var viz = Graphviz.fromGraph(exp);
      try {
        viz.render(Format.SVG)
            .toOutputStream(
                new PrintStream(
                    new File(OUT, fqnToFlatFilename(fd.getFullyQualifiedName()) + "-sizes.svg")));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    for (var fd : program.getFunctionDefinitions().values()) {
      System.out.println(fd.getFullyQualifiedName() + " ∷ " + fd.getInferredSignature());
    }
  }

  @Test
  void fiddle() throws Exception {}

  private enum ExpectedResult {
    SAT,
    UNSAT,
    UNKNOWN
  }
}