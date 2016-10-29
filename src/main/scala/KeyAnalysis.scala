
import java.io.ByteArrayInputStream

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

import org.joda.time.DateTime

import scala.collection.JavaConverters._

object KeyAnalysis {

  import scalikejdbc._

  Class.forName("org.postgresql.Driver")

  ConnectionPool.singleton("jdbc:postgresql://localhost/gpg_analysis", "gpg", "")

  implicit val session = AutoSession

  def getAlgorithmForKey(key: PGPPublicKey): String = {
    key.getAlgorithm match {
      case PublicKeyAlgorithmTags.RSA_ENCRYPT |
      PublicKeyAlgorithmTags.RSA_GENERAL |
      PublicKeyAlgorithmTags.RSA_SIGN        => "RSA"
      case PublicKeyAlgorithmTags.DSA             => "DSA"
      case PublicKeyAlgorithmTags.EC              => "EC"
      case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT |
      PublicKeyAlgorithmTags.ELGAMAL_GENERAL => "ElGamal"
      case PublicKeyAlgorithmTags.ECDSA           => "ECDSA"
      case _ => "Unknown"
    }
  }

  def analyzeAndSave(keyData: String, domain: String, email: String, industry: Option[String], size: Option[String]): Unit = {

    val pgpKey = try {
      val keyring = new PGPPublicKeyRing(PGPUtil.getDecoderStream(new ByteArrayInputStream(keyData.getBytes)), new JcaKeyFingerprintCalculator)
      keyring.getPublicKey()
    } catch {
      case e: Exception => {
        println(s"Exception getting key for ${email}")
        println(e.getStackTrace)
        return
      }
    }

    val algorithm = getAlgorithmForKey(pgpKey)

    val createdAt = new DateTime(pgpKey.getCreationTime)

    val expiresAt = pgpKey.getValidDays match {
      case 0 => null
      case x => new DateTime(createdAt).plusDays(x)
    }

    val bitStrength = pgpKey.getBitStrength.toString

    val revoked = pgpKey.isRevoked

    val version = pgpKey.getVersion

    val userAttributes = pgpKey.getUserAttributes.asScala.mkString("\n")

    val insertQuery = sql"""insert into key_analysis (
      raw_key,
      domain,
      email,
      key_created_at,
      key_expires_at,
      key_algorithm,
      key_bit_strength,
      key_revoked,
      key_version,
      key_user_attributes,
      industry,
      size
    ) VALUES (
      ${keyData},
      ${domain},
      ${email},
      ${createdAt},
      ${expiresAt},
      ${algorithm},
      ${bitStrength},
      ${revoked},
      ${version},
      ${userAttributes},
      ${industry},
      ${size}
    );"""

    insertQuery.update.apply

  }
}
