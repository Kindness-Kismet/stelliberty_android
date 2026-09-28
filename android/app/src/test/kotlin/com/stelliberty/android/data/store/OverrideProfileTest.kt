package com.stelliberty.android.data.store

import com.stelliberty.android.domain.model.OverrideFormat
import com.stelliberty.android.domain.model.OverrideProfile
import com.stelliberty.android.domain.model.OverrideSourceType
import com.stelliberty.android.domain.model.Subscription
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OverrideProfileTest {
    @Test
    fun desktopProfilePreservesEnumsTimesAndKeys() {
        val profile = StoreJson.decodeFromString<OverrideProfile>(
            """{
              "Id": "0123456789abcdef0123456789abcdef",
              "Name": "Example",
              "SourceType": 1,
              "Format": 1,
              "SourceLocation": "https://example.com/override.js",
              "CreatedAt": "2026-01-01T08:00:00+08:00",
              "LastUpdatedAt": null,
              "UpdateProxyMode": 1
            }""".trimIndent(),
        )
        assertEquals(OverrideSourceType.Remote, profile.sourceType)
        assertEquals(OverrideFormat.JavaScript, profile.format)
        assertEquals(SubscriptionUpdateProxyMode.SystemProxy, profile.updateProxyMode)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), profile.createdAt)
        val encoded = StoreJson.encodeToString(OverrideProfile.serializer(), profile)
        val obj = StoreJson.parseToJsonElement(encoded).jsonObject
        assertEquals(
            setOf("Id", "Name", "SourceType", "Format", "SourceLocation", "CreatedAt", "LastUpdatedAt", "UpdateProxyMode"),
            obj.keys,
        )
        assertEquals("1", obj.getValue("SourceType").jsonPrimitive.content)
        assertEquals("1", obj.getValue("Format").jsonPrimitive.content)
        assertEquals("1", obj.getValue("UpdateProxyMode").jsonPrimitive.content)
        assertEquals(JsonNull, obj["LastUpdatedAt"])
        assertEquals(profile, StoreJson.decodeFromString<OverrideProfile>(encoded))
    }

    @Test
    fun selectedOverridesFollowPreferenceThenSelectionOrderWithoutDuplicates() {
        val subscription = Subscription(
            id = "example",
            name = "Example",
            sourceLocation = "",
            isLocalFile = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            overrideIds = listOf("a", "b", "c", "a"),
            overrideSortPreference = listOf("unselected", "c", "c", "a"),
        )
        assertEquals(listOf("c", "a", "b"), subscription.orderedOverrideIds)
        assertEquals(emptyList(), subscription.copy(overrideIds = emptyList()).orderedOverrideIds)
    }
}
