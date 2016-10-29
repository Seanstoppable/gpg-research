
import scala.io.Source

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

import play.api.libs.json._

import org.joda.time.DateTime

object Scraper {

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

            KeyAnalysis.analyzeAndSave(keyResult, domain, id, company.category, company.employees)
          }
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {

    val source: String = Source.fromFile("companies.json").getLines.mkString
    val companies: Seq[Company]  = Json.parse(source).validate[Seq[Company]].get
    companies.foreach { company =>
      collectResultsForCompany(company)
    }
  }

}
