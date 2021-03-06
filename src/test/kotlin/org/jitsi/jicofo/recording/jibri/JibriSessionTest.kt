/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2020 - Present, 8x8 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jicofo.recording.jibri

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.jitsi.jicofo.FocusBundleActivator
import org.jitsi.osgi.ServiceUtils2
import org.jitsi.protocol.xmpp.XmppConnection
import org.jitsi.test.concurrent.FakeScheduledExecutorService
import org.jitsi.utils.logging.Logger
import org.jitsi.xmpp.extensions.jibri.JibriIq
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XMPPError
import org.jxmpp.jid.impl.JidCreate

class JibriSessionTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    mockkStatic(ServiceUtils2::class)
    val owner: JibriSession.Owner = mockk(relaxed = true)
    val roomName = JidCreate.entityBareFrom("room@bar.com/baz")
    val initiator = JidCreate.bareFrom("foo@bar.com/baz")
    val pendingTimeout = 60L
    val maxNumRetries = 2
    val xmppConnection: XmppConnection = mockk()
    val executor: FakeScheduledExecutorService = spyk()
    val jibriList = mutableListOf(
        JidCreate.bareFrom("jibri1@bar.com"),
        JidCreate.bareFrom("jibri2@bar.com"),
        JidCreate.bareFrom("jibri3@bar.com")
    )
    val detector: JibriDetector = mockk {
        every { selectJibri() } returnsMany (jibriList)
        every { isAnyInstanceConnected } returns true
        every { memberHadTransientError(any()) } answers {
            // Simulate the real JibriDetector logic and put the Jibri at the back of the list
            jibriList.remove(arg(0))
            jibriList.add(arg(0))
        }
        every { addHandler(any()) } returns Unit
        every { removeHandler(any()) } returns Unit
    }
    val logger: Logger = mockk(relaxed = true)

    val jibriSession = JibriSession(
        owner,
        roomName,
        initiator,
        pendingTimeout,
        maxNumRetries,
        xmppConnection,
        executor,
        detector,
        false /* isSIP */,
        null /* sipAddress */,
        "displayName",
        "streamID",
        "youTubeBroadcastId",
        "sessionId",
        "applicationData",
        logger
    )

    FocusBundleActivator.bundleContext = mockk(relaxed = true)

    context("When sending a request to a Jibri to start a session throws an error") {
        val iqRequests = mutableListOf<IQ>()
        every { xmppConnection.sendPacketAndGetReply(capture(iqRequests)) } answers {
            // First return error
            IQ.createErrorResponse(arg(0), XMPPError.Condition.service_unavailable)
        } andThen {
            // Then return a successful response
            JibriIq().apply {
                status = JibriIq.Status.PENDING
                from = (arg(0) as IQ).to
            }
        }
        context("Trying to start a Jibri session") {
            should("retry with another jibri") {
                jibriSession.start()
                verify(exactly = 2) { xmppConnection.sendPacketAndGetReply(any()) }
                iqRequests shouldHaveSize 2
                iqRequests[0].to shouldNotBe iqRequests[1].to
            }
        }
        context("and that's the only Jibri") {
            every { detector.selectJibri() } returns JidCreate.bareFrom("solo@bar.com")
            every { xmppConnection.sendPacketAndGetReply(capture(iqRequests)) } answers {
                // First return error
                IQ.createErrorResponse(arg(0), XMPPError.Condition.service_unavailable)
            }
            context("trying to start a jibri session") {
                should("give up after exceeding the retry count") {
                    shouldThrow<JibriSession.StartException> {
                        jibriSession.start()
                    }
                    verify(exactly = maxNumRetries + 1) { xmppConnection.sendPacketAndGetReply(any()) }
                }
            }
        }
    }
    context("Trying to start a session with a Jibri that is busy") {
        val iq = slot<IQ>()
        // First return busy, then pending
        every { xmppConnection.sendPacketAndGetReply(capture(iq)) } answers {
            JibriIq().apply {
                type = IQ.Type.result
                from = iq.captured.to
                to = iq.captured.from
                shouldRetry = true
                status = JibriIq.Status.OFF
                failureReason = JibriIq.FailureReason.BUSY
            }
        } andThen {
            JibriIq().apply {
                type = IQ.Type.result
                from = iq.captured.to
                to = iq.captured.from
                shouldRetry = true
                status = JibriIq.Status.PENDING
            }
        }
        jibriSession.start()
        should("not count as a transient error") {
            verify(exactly = 0) { detector.memberHadTransientError(any()) }
        }
        should("retry with another jibri") {
            verify(exactly = 2) { xmppConnection.sendPacketAndGetReply(any()) }
        }
    }
})
