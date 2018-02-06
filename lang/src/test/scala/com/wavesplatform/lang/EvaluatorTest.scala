package com.wavesplatform.lang

import com.wavesplatform.lang.Evaluator.Context
import com.wavesplatform.lang.Terms._
import org.scalatest.{Matchers, PropSpec}
import org.scalatest.prop.PropertyChecks
import scodec.bits.ByteVector

class EvaluatorTest extends PropSpec with PropertyChecks with Matchers with ScriptGen with NoShrink {

  private def ev(c: Expr): Either[_, _] = Evaluator.apply(Context(new HeightDomain(1), Map.empty), c)

  private def simpleDeclarationAndUsage(i: Int) = Block(Some(LET("x", CONST_INT(i))), REF("x"))

  property("successful on very deep expressions (stack overflow check)") {
    val term = (1 to 100000).foldLeft[Terms.Expr](CONST_INT(0))((acc, _) => SUM(acc, CONST_INT(1)))
    ev(term) shouldBe Right(100000)
  }

  property("successful on unused let") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        CONST_INT(3)
      )) shouldBe Right(3)
  }

  property("successful on x = y") {
    ev(
      Block(Some(LET("x", CONST_INT(3))),
            Block(
              Some(LET("y", REF("x"))),
              SUM(REF("x"), REF("y"))
            ))) shouldBe Right(6)
  }

  property("successful on simple get") {
    ev(simpleDeclarationAndUsage(3)) shouldBe Right(3)
  }

  property("successful on get used further in expr") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        EQ(REF("x"), CONST_INT(2))
      )) shouldBe Right(false)
  }

  property("successful on multiple lets") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        Block(Some(LET("y", CONST_INT(3))), EQ(REF("x"), REF("y")))
      )) shouldBe Right(true)
  }

  property("successful on multiple lets with expression") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        Block(Some(LET("y", SUM(CONST_INT(3), CONST_INT(0)))), EQ(REF("x"), REF("y")))
      )) shouldBe Right(true)
  }

  property("successful on deep type resolution") {
    ev(
      IF(EQ(CONST_INT(1), CONST_INT(2)), simpleDeclarationAndUsage(3), CONST_INT(4))
    ) shouldBe Right(4)
  }

  property("successful on same value names in different branches") {
    ev(
      IF(EQ(CONST_INT(1), CONST_INT(2)), simpleDeclarationAndUsage(3), simpleDeclarationAndUsage(4))
    ) shouldBe Right(4)
  }

  property("fails if override") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        Block(Some(LET("x", SUM(CONST_INT(3), CONST_INT(0)))), EQ(REF("x"), CONST_INT(1)))
      )) should produce("already defined")
  }

  property("fails if types do not match") {
    ev(
      Block(
        Some(LET("x", CONST_INT(3))),
        Block(Some(LET("y", EQ(CONST_INT(3), CONST_INT(0)))), EQ(REF("x"), REF("y")))
      )) should produce("Typecheck failed")
  }

  property("fails if definition not found") {
    ev(SUM(REF("x"), CONST_INT(2))) should produce("Cannot resolve type of x")
  }

  property("fails if 'IF' branches lead to different types") {
    ev(
      IF(EQ(CONST_INT(1), CONST_INT(2)), CONST_INT(0), CONST_BYTEVECTOR(ByteVector.empty))
    ) should produce("Typecheck failed")
  }

  property("Typechecking") {
    ev(EQ(CONST_INT(2), CONST_INT(2))) shouldBe Right(true)
  }

  property("successful Typechecking Some") {
    ev(EQ(SOME(CONST_INT(2)), SOME(CONST_INT(2)))) shouldBe Right(true)
  }

  property("successful Typechecking Option") {
    ev(EQ(SOME(CONST_INT(2)), NONE)) shouldBe Right(false)
    ev(EQ(NONE, SOME(CONST_INT(2)))) shouldBe Right(false)
  }

  property("successful nested Typechecking Option") {
    ev(EQ(SOME(SOME(SOME(CONST_INT(2)))), NONE)) shouldBe Right(false)
    ev(EQ(SOME(NONE), SOME(SOME(CONST_INT(2))))) shouldBe Right(false)
  }

  property("fails if nested Typechecking Option finds mismatch") {
    ev(EQ(SOME(SOME(FALSE)), SOME(SOME(CONST_INT(2))))) should produce("Typecheck failed")
  }

  property("successful GET/IS_DEFINED") {
    ev(IS_DEFINED(NONE)) shouldBe Right(false)
    ev(IS_DEFINED(SOME(CONST_INT(1)))) shouldBe Right(true)
    ev(GET(SOME(CONST_INT(1)))) shouldBe Right(1)
  }

  property("resolveType") {
    Evaluator.resolveType(Map.empty, SOME(CONST_INT(3))).result shouldBe Right(OPTION(INT))
    Evaluator.resolveType(Map.empty, NONE).result shouldBe Right(OPTION(NOTHING))
    Evaluator.resolveType(Map.empty, IF(TRUE, SOME(CONST_INT(3)), NONE)).result shouldBe Right(OPTION(INT))
    Evaluator.resolveType(Map.empty, IF(TRUE, NONE, SOME(CONST_INT(3)))).result shouldBe Right(OPTION(INT))
    Evaluator.resolveType(Map.empty, IF(TRUE, NONE, NONE)).result shouldBe Right(OPTION(NOTHING))
    Evaluator.resolveType(Map.empty, IF(TRUE, SOME(FALSE), SOME(CONST_INT(3)))).result should produce("Typecheck")
  }

  property("successful resolve strongest type") {
    ev(GET(IF(TRUE, SOME(CONST_INT(3)), SOME(CONST_INT(2))))) shouldBe Right(3)
    ev(GET(IF(TRUE, SOME(CONST_INT(3)), NONE))) shouldBe Right(3)
    ev(SUM(CONST_INT(1), GET(IF(TRUE, SOME(CONST_INT(3)), NONE)))) shouldBe Right(4)
  }

}