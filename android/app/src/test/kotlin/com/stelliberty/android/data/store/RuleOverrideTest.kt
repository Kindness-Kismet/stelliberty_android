package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.EditableRule
import com.stelliberty.android.domain.model.RuleKeys
import com.stelliberty.android.viewmodel.RuleOverrideRow
import com.stelliberty.android.viewmodel.mergeRuleOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class RuleOverrideTest {
    @Test
    fun ruleKeysMatchNativeNormalization() {
        assertEquals(
            "IP-CIDR\u001f10.0.0.0/8\u001fDIRECT\u001fNO-RESOLVE",
            RuleKeys.create("ip-cidr", " 10.0.0.0/8 ", "direct", "no-resolve"),
        )
        assertEquals("MATCH\u001f\u001fPROXY\u001f", RuleKeys.create("MATCH", "", "Proxy", ""))
        assertEquals("DOMAIN\u001fA B", RuleKeys.match("domain", "a　\t b"))
    }

    @Test
    fun renderSkipsPayloadForMatchAndBlankOptions() {
        assertEquals("MATCH,Proxy", EditableRule("custom-a", "MATCH", "ignored", " Proxy ", "").render())
        assertEquals(
            "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
            EditableRule("custom-b", "IP-CIDR", "10.0.0.0/8", "DIRECT", "no-resolve").render(),
        )
    }

    @Test
    fun customRulesGoBeforeFirstMatchWithoutOrder() {
        val merged = mergeRuleOrder(listOf(builtin("a"), builtin("b"), match()), listOf(custom("x")), emptyList())
        assertEquals(listOf("builtin:a", "builtin:b", "custom:x", "builtin:MATCH"), merged.map { it.orderId })
    }

    @Test
    fun savedOrderDedupesAndPlacesUnorderedRules() {
        val merged = mergeRuleOrder(
            builtin = listOf(builtin("a"), builtin("b"), builtin("new"), match()),
            custom = listOf(custom("x"), custom("y")),
            order = listOf("custom:x", "builtin:b", "custom:x", "builtin:MATCH", "builtin:a", "builtin:gone"),
        )
        assertEquals(
            listOf("custom:x", "builtin:b", "custom:y", "builtin:MATCH", "builtin:a", "builtin:new"),
            merged.map { it.orderId },
        )
    }

    @Test
    fun desktopFileRoundTripsWithPascalCaseKeys() {
        val text = """{
          "Items": [{
            "SubscriptionId": "0123456789abcdef0123456789abcdef",
            "CustomRules": [{"Id": "custom-1", "Type": "DOMAIN", "Payload": "example.com", "Proxy": "DIRECT", "Options": "", "IsEnabled": false}],
            "DisabledBuiltinRuleKeys": ["MATCH\u001f\u001fPROXY\u001f"],
            "RuleOrder": []
          }],
          "Templates": [{"Id": "template-1", "Name": "Example", "Rules": []}]
        }""".trimIndent()
        val file = StoreJson.decodeFromString(RuleOverrideFile.serializer(), text)
        assertEquals(false, file.items.single().customRules.single().isEnabled)
        assertEquals("MATCH\u001f\u001fPROXY\u001f", file.items.single().disabledBuiltinRuleKeys.single())
        val encoded = StoreJson.parseToJsonElement(StoreJson.encodeToString(RuleOverrideFile.serializer(), file)).jsonObject
        assertEquals(setOf("Items", "Templates"), encoded.keys)
        assertEquals(
            setOf("Id", "Type", "Payload", "Proxy", "Options", "IsEnabled"),
            encoded.getValue("Items").jsonArray[0].jsonObject.getValue("CustomRules").jsonArray[0].jsonObject.keys,
        )
        assertEquals(file, StoreJson.decodeFromString(RuleOverrideFile.serializer(), encoded.toString()))
    }

    private fun builtin(key: String) = row("builtin:$key", key, null)

    private fun match() = row("builtin:MATCH", "MATCH", null, type = "MATCH")

    private fun custom(id: String) = row("custom:$id", id, id)

    private fun row(orderId: String, key: String, customId: String?, type: String = "DOMAIN") = RuleOverrideRow(
        orderId = orderId,
        key = key,
        matchKey = key,
        type = type,
        payload = key,
        proxy = "DIRECT",
        options = "",
        customId = customId,
        isEnabled = true,
    )
}
