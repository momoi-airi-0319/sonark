package md.oak.sonark.utils

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.Serializable

@Serializable
data class TestAccount(val email: String, val token: String)

@Serializable
data class TestConfig(
    val account_a: TestAccount? = null,
    val account_b: TestAccount? = null
)

object TestConfigLoader {
    fun loadConfig(): TestConfig {
        val arguments = InstrumentationRegistry.getArguments()
        val emailA = arguments.getString("account_a_email")
        val tokenA = arguments.getString("account_a_token")
        val emailB = arguments.getString("account_b_email")
        val tokenB = arguments.getString("account_b_token")

        return TestConfig(
            account_a = if (emailA != null && tokenA != null) TestAccount(emailA, tokenA) else null,
            account_b = if (emailB != null && tokenB != null) TestAccount(emailB, tokenB) else null
        )
    }
}
