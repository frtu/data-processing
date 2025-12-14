package com.github.frtu.dataprocessing.framework

import com.github.frtu.samples.dataprocessing.model.input.Email
import com.github.frtu.samples.dataprocessing.model.output.STATUS
import com.github.frtu.samples.dataprocessing.transform.EmailTransformer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@ExtendWith(MockKExtension::class)
class FlinkTransformOperatorTest {

	@Test
	fun `should transform Email to EmailEntity`() {
		// given
		val inputEmail = Email(
			subject = "Test Subject",
			content = "Test email content",
			id = UUID.randomUUID(),
			createdTimeMillis = 1640995200000L, // 2022-01-01T00:00:00Z
			updatedTimeMillis = 1640995260000L  // 2022-01-01T00:01:00Z
		)
		val transformContext = mockk<TransformContext>()
		val emailTransform = EmailTransformer()

		// when
		val result = emailTransform.process(inputEmail, transformContext)

		// then
		result shouldNotBe null
		result!!.subject shouldBe "Test Subject"
		result.content shouldBe "Test email content"
		result.status shouldBe STATUS.INIT
		result.receiver shouldBe null
		result.id shouldBe null

		// Verify time conversion from milliseconds to LocalDateTime
		val expectedCreationTime = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(1640995200000L),
			ZoneId.systemDefault()
		)
		val expectedUpdateTime = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(1640995260000L),
			ZoneId.systemDefault()
		)
		result.creationTime shouldBe expectedCreationTime
		result.updateTime shouldBe expectedUpdateTime
	}
}