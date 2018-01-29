package scorex.transaction.smart

import com.google.common.primitives.{Bytes, Longs}
import com.wavesplatform.state2._
import monix.eval.Coeval
import play.api.libs.json.Json
import scorex.account._
import scorex.crypto.EllipticCurveImpl
import scorex.serialization.Deser
import scorex.transaction.TransactionParser.{KeyLength, TransactionType}
import scorex.transaction.ValidationError.GenericError
import scorex.transaction._
import scorex.account.AddressScheme

import scala.util.{Failure, Success, Try}

case class SetScriptTransaction private(version: Byte,
                                        sender: PublicKeyAccount,
                                        script: Script,
                                        fee: Long,
                                        timestamp: Long,
                                        proofs: Proofs) extends ProvenTransaction with FastHashId {

  val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(
    Array(version),
    Array(AddressScheme.current.chainId),
    sender.publicKey,
    Deser.serializeArray(script.bytes().arr),
    Longs.toByteArray(fee),
    Longs.toByteArray(timestamp)))

  override val transactionType = TransactionType.SetScriptTransaction

  override val assetFee = (None, fee)
  override val json = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "version" -> version,
    "script" -> script.text)
  )

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte), bodyBytes(), proofs.bytes()))
}

object SetScriptTransaction {

  def parseTail(bytes: Array[Byte]): Try[SetScriptTransaction] = Try {
    val version = bytes(0)
    val chainId = bytes(1)
    val sender = PublicKeyAccount(bytes.slice(2, KeyLength + 2))
    val (scriptBytes, scriptEnd) = Deser.parseArraySize(bytes, KeyLength + 2)
    val fee = Longs.fromByteArray(bytes.slice(scriptEnd, scriptEnd + 8))
    val timestamp = Longs.fromByteArray(bytes.slice(scriptEnd + 8, scriptEnd + 16))
    (for {
      _ <- Either.cond(chainId == AddressScheme.current.chainId, (), GenericError(s"Wrong chainId: $chainId but expected: ${AddressScheme.current.chainId}"))
      _ <- Either.cond(version == 1, (), GenericError(s"Unsupported SetScriptTransaction version: ${version.toInt}"))
      script <- Script.fromBytes(scriptBytes)
      proofs <- Proofs.fromBytes(bytes.drop(scriptEnd + 16))
      tx <- create(sender, script, fee, timestamp, proofs)
    } yield tx).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten


  def create(sender: PublicKeyAccount,
             script: Script,
             fee: Long,
             timestamp: Long,
             proofs: Proofs): Either[ValidationError, SetScriptTransaction] =
    if (fee <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(new SetScriptTransaction(1, sender, script, fee, timestamp, proofs))
    }


  def selfSigned(sender: PrivateKeyAccount,
                 script: Script,
                 fee: Long,
                 timestamp: Long): Either[ValidationError, SetScriptTransaction] = create(sender, script, fee, timestamp, Proofs.empty).right.map { unsigned =>
    unsigned.copy(proofs = Proofs.create(Seq(ByteStr(EllipticCurveImpl.sign(sender, unsigned.bodyBytes())))).explicitGet())
  }
}