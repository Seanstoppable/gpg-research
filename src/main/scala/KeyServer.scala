
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
