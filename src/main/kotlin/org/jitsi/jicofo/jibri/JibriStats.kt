/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 *
 */
package org.jitsi.jicofo.jibri;

import org.jitsi.jicofo.recording.jibri.JibriSession
import org.json.simple.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class JibriStats {
    /**
     * How many times a Jibri SIP call has failed to start.
     */
    private val totalSipCallFailures = AtomicInteger()

    /**
     * How many times Jibri live streaming has failed to start.
     */
    private val totalLiveStreamingFailures = AtomicInteger()

    /**
     * How many times Jibri recording has failed to start.
     */
    private val totalRecordingFailures = AtomicInteger()

    fun sipCallFailed() {
        totalSipCallFailures.incrementAndGet()
    }

    fun liveStreamingFailed() {
        totalLiveStreamingFailures.incrementAndGet()
    }

    fun recordingFailed() {
        totalRecordingFailures.incrementAndGet()
    }

    fun sessionFailed(type: JibriSession.Type) = when (type) {
        JibriSession.Type.SIP_CALL -> sipCallFailed()
        JibriSession.Type.LIVE_STREAMING -> liveStreamingFailed()
        JibriSession.Type.RECORDING -> recordingFailed()
    }

    fun toJson() = JSONObject().apply {
        put("total_live_streaming_failures", totalLiveStreamingFailures.get())
        put("total_recording_failures", totalRecordingFailures.get())
        put("total_sip_call_failures", totalSipCallFailures.get())
    }
}