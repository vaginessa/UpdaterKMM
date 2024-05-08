import data.AuthorizeHelper
import data.LoginHelper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.InternalAPI
import misc.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val loginUrl = "https://account.xiaomi.com/pass/serviceLogin"
private const val loginAuth2Url = "https://account.xiaomi.com/pass/serviceLoginAuth2"

@OptIn(ExperimentalEncodingApi::class, InternalAPI::class)
suspend fun login(account: String, password: String, global: Boolean): Boolean {
    if (account.isEmpty() || password.isEmpty()) return false

    val client = HttpClient()
    val response1 = client.get { url(loginUrl) }

    val md5Hash = md5Hash(password)
    val sign = response1.request.url.parameters["_sign"]?.replace("2&V1_passport&", "") ?: return false
    val sid = if (global) "miuiota_intl" else "miuiromota"
    val locale = if (global) "en_US" else "zh_CN"
    val data = "_json=true&bizDeviceType=&user=$account&hash=$md5Hash&sid=$sid&_sign=$sign&_locale=$locale"
    val response2 = client.post(loginAuth2Url) {
        body = TextContent(data, ContentType.Application.FormUrlEncoded)
    }

    val authStr = response2.body<String>().replace("&&&START&&&", "")
    val authJson = json.decodeFromString<AuthorizeHelper>(authStr)
    val description = authJson.description
    val nonce = authJson.nonce
    val ssecurity = authJson.ssecurity
    val location = authJson.location
    val userId = authJson.userId.toString()
    val accountType = if (global) "GL" else "CN"
    val authResult = if (authJson.result == "ok") "1" else "0"

    if (description != "成功" || nonce == null || ssecurity == null || location == null || userId.isEmpty()) return false

    val sha1Hash = sha1Hash("nonce=$nonce&$ssecurity")
    val clientSign = Base64.Default.encode(sha1Hash)

    val newUrl = "$location&_userIdNeedEncrypt=true&clientSign=$clientSign"
    val response3 = client.get(newUrl)
    val cookies = response3.headers["Set-Cookie"].toString().split("; ")[0].split("; ")[0]
    val serviceToken = cookies.split("serviceToken=")[1].split(";")[0]

    val loginInfo = LoginHelper(accountType, authResult, description, ssecurity, serviceToken, userId)

    // TODO: Save loginInfo

    return true
}

fun logout() {
    // TODO
}


expect fun md5Hash(input: String): String

expect fun sha1Hash(input: String): ByteArray