package xyz.leutgeb.lorenz.atlas.typing.simple.types;

import java.util.Collection;
import java.util.Collections;
import xyz.leutgeb.lorenz.atlas.unification.Equivalence;
import xyz.leutgeb.lorenz.atlas.unification.TypeMismatch;

@Deprecated
public class BaseType /* implements Type */ {
  public static final BaseType INSTANCE = new BaseType();

  // @Override
  public Collection<Equivalence> decompose(Type b) throws TypeMismatch {
    // if (!(b instanceof BaseType)) {
    //  throw new TypeMismatch(this, b);
    // }
    return Collections.emptyList();
  }

  // @Override
  public String toHaskell() {
    return "Integer";
  }
}
