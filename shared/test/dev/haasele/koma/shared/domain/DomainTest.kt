package dev.haasele.koma.shared.domain

import dev.haasele.koma.shared.json.JsonQuery
import dev.haasele.koma.shared.net.DnsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusCodeMatchingTest {

    @Test
    fun `ranges and single codes are honoured`() {
        val accepted = listOf("200-299", "301")
        assertTrue(accepted.matchesStatusCode(204))
        assertTrue(accepted.matchesStatusCode(301))
        assertFalse(accepted.matchesStatusCode(302))
        assertFalse(accepted.matchesStatusCode(500))
    }
}

class MonitorValidationTest {

    @Test
    fun `http monitor requires a url`() {
        val errors = Monitor(name = "Site", type = MonitorType.HTTP).validate()
        assertTrue(errors.any { it.contains("URL") })
    }

    @Test
    fun `port monitor requires host and valid port`() {
        val errors = Monitor(
            name = "DB",
            type = MonitorType.PORT,
            config = MonitorConfig(hostname = "db.example.com", port = 70_000),
        ).validate()
        assertTrue(errors.any { it.contains("Port") })
    }

    @Test
    fun `a complete monitor passes`() {
        val monitor = Monitor(
            name = "Site",
            type = MonitorType.HTTP,
            config = MonitorConfig(url = "https://example.com"),
        )
        assertTrue(monitor.validate().isEmpty())
    }

}

class ConditionEvaluatorTest {

    private val variables = mapOf(
        "response_code" to listOf("200"),
        "record" to listOf("1.2.3.4", "5.6.7.8"),
    )

    @Test
    fun `empty conditions always pass`() {
        assertTrue(ConditionEvaluator.evaluate(emptyList(), variables))
    }

    @Test
    fun `equality on any matching value passes`() {
        val group = ConditionGroup(listOf(Condition("record", ConditionOperator.EQUALS, "5.6.7.8")))
        assertTrue(ConditionEvaluator.evaluate(listOf(group), variables))
    }

    @Test
    fun `numeric comparison works on string values`() {
        val group = ConditionGroup(listOf(Condition("response_code", ConditionOperator.LESS_THAN, "300")))
        assertTrue(ConditionEvaluator.evaluate(listOf(group), variables))
    }

    @Test
    fun `or joins alternatives within a group`() {
        val group = ConditionGroup(
            listOf(
                Condition("response_code", ConditionOperator.EQUALS, "500"),
                Condition("response_code", ConditionOperator.EQUALS, "200", join = ConditionJoin.OR),
            ),
        )
        assertTrue(ConditionEvaluator.evaluate(listOf(group), variables))
    }

    @Test
    fun `missing variables fail the condition`() {
        val group = ConditionGroup(listOf(Condition("absent", ConditionOperator.EQUALS, "x")))
        assertFalse(ConditionEvaluator.evaluate(listOf(group), variables))
    }
}

class JsonQueryTest {

    private val payload = """
        {"status":"ok","data":{"items":[{"name":"first","value":10},{"name":"second","value":20}]}}
    """.trimIndent()

    @Test
    fun `root field is extracted`() {
        assertEquals(listOf("ok"), JsonQuery.evaluate(payload, "$.status"))
    }

    @Test
    fun `nested array index is extracted`() {
        assertEquals(listOf("second"), JsonQuery.evaluate(payload, "$.data.items[1].name"))
    }

    @Test
    fun `wildcard collects every element`() {
        assertEquals(listOf("10", "20"), JsonQuery.evaluate(payload, "$.data.items[*].value"))
    }

    @Test
    fun `unknown path yields nothing`() {
        assertTrue(JsonQuery.evaluate(payload, "$.missing.field").isEmpty())
    }
}

class DnsWireFormatTest {

    @Test
    fun `query encodes labels and type`() {
        val query = DnsClient.buildQuery(0x1234, "example.com", DnsRecordType.A)
        assertEquals(0x12, query[0].toInt() and 0xff)
        assertEquals(0x34, query[1].toInt() and 0xff)
        // 7 'example' 3 'com' 0
        assertEquals(7, query[12].toInt())
        assertEquals("example", query.decodeToString(13, 20))
        assertEquals(3, query[20].toInt())
        assertEquals("com", query.decodeToString(21, 24))
        assertEquals(0, query[24].toInt())
        assertEquals(1, query[26].toInt()) // QTYPE A
    }

    @Test
    fun `response with one A record is parsed`() {
        val response = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(), // id and flags
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, // counts
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0x00, 0x01, 0x00, 0x01, // qtype, qclass
            0xc0.toByte(), 0x0c, // pointer to the question name
            0x00, 0x01, 0x00, 0x01, // type A, class IN
            0x00, 0x00, 0x00, 0x3c, // ttl
            0x00, 0x04, // rdlength
            93.toByte(), 184.toByte(), 216.toByte(), 34.toByte(),
        )
        assertEquals(listOf("93.184.216.34"), DnsClient.parseResponse(response, DnsRecordType.A))
    }
}
