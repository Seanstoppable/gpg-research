
import java.io.ByteArrayInputStream

import scala.io.Source

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

import play.api.libs.json._

import org.joda.time.DateTime

object KeyServer {
  import dispatch._, Defaults._
  val hkpServiceUrl = "http://keyserver.ubuntu.com:11371/pks/lookup"
  val hkpQueryUrl = hkpServiceUrl+"?op=index&options=mr&search="

  def queryKeysForDomain(query: String): Option[String] = {
    val request = url(hkpQueryUrl+query)
    val resp = Http(request OK as.String).option
    resp()
  }

  def queryForRawKey(key: String): String = {
    val keyQuery = url(hkpKeyUrl+key)
    val keyResp = Http(keyQuery OK as.String)
    keyResp()
  }

 val hkpKeyUrl = hkpServiceUrl+"?op=get&options=mr&search=0x"
}

object Scraper {

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

  def getKeyPairsFromString(results: String): Seq[Tuple2[String, String]] = {
    val keyRegex = "^pub:([A-Z0-9]+):.*".r
    val idRegex = "^uid:.*<([a-zA-Z0-9@.]+)>:.*".r
    val resultsArray = results.split("\n")
    val keys = resultsArray.filter(keyRegex.pattern.matcher(_).matches).map { x => x match { case keyRegex(m1) => m1 } }
    val ids = resultsArray.filter(idRegex.pattern.matcher(_).matches).map { x => x match { case idRegex(m1) => m1 } }

    return ids.zip(keys)
  }

  def identityMatchesDomain(id: String, domain: String): Boolean = {
    return (id.endsWith(s"@${domain}")
            || id.endsWith(s".${domain}"))
  }

  def collectResultsForCompany(company: Company): Unit = {

    val domain = company.domain

    KeyServer.queryKeysForDomain(domain) match {
      case None => {}
      case Some(results: String) => {

        val pairs = getKeyPairsFromString(results)

        pairs.foreach { case (id, key) =>
          //keyserver search is fuzzy matching, and can't do completely by domain
          //double check here
          if (identityMatchesDomain(id, domain)) {
            println(id + " " + key)

            val keyResult = KeyServer.queryForRawKey(key)

            analyzeAndSave(keyResult, domain, id, company.category, company.employees)
          }
        }
      }
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

  def main(args: Array[String]): Unit = {

    val source: String = Source.fromFile("companies.json").getLines.mkString
    val companies: Seq[Company]  = Json.parse(source).validate[Seq[Company]].get
    companies.foreach { company =>
      collectResultsForCompany(company)
    }
  }

}

