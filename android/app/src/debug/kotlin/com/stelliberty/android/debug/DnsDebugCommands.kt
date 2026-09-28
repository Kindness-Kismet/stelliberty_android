package com.stelliberty.android.debug

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import com.stelliberty.android.data.api.MihomoConnectionManager
import com.stelliberty.android.domain.model.DnsQueryTypes
import com.stelliberty.android.domain.model.dnsTypeName

internal fun runDnsCommand(action: String, arg: String?, extras: Bundle?): Bundle {
    val command = "dns.$action"
    val repository = koin<MihomoConnectionManager>().repository.value
        ?: return debugResult(false, "Proxy is not running", command)
    return runBlocking {
        when (action) {
            "query" -> {
                val name = extras.target(arg) ?: return@runBlocking debugResult(false, "Missing domain", command)
                val type = (extras.string(EXTRA_TYPE) ?: "A").uppercase()
                if (type !in DnsQueryTypes) {
                    return@runBlocking debugResult(
                        false,
                        "Invalid type: $type (want ${DnsQueryTypes.joinToString("/")})",
                        command,
                        name,
                    )
                }
                repository.queryDns(name, type).fold(
                    onSuccess = { response ->
                        val answers = response.Answer.joinToString("; ") {
                            "${dnsTypeName(it.type)} ${it.data} ttl=${it.TTL}"
                        }
                        debugResult(
                            isOk = true,
                            message = "status=${response.Status}, ${response.Answer.size} answers",
                            command = command,
                            target = "$name/$type",
                            data = answers.ifEmpty { "no answer" },
                        )
                    },
                    onFailure = { debugResult(false, it.describeForDebug(), command, "$name/$type") },
                )
            }

            "flush_cache" -> repository.flushDnsCache().fold(
                onSuccess = { debugResult(true, "DNS cache flushed", command) },
                onFailure = { debugResult(false, it.describeForDebug(), command) },
            )

            "flush_fakeip" -> repository.flushFakeIp().fold(
                onSuccess = { debugResult(true, "fake-ip pool flushed", command) },
                onFailure = { debugResult(false, it.describeForDebug(), command) },
            )

            else -> debugResult(false, "Unknown dns action: $action", command, arg)
        }
    }
}
