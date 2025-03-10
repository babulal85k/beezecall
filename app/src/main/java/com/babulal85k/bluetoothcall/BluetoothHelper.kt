package com.babulal85k.bluetoothcall

import android.media.*
import android.bluetooth.*
import kotlinx.coroutines.*
import java.io.IOException

object BluetoothHelper {
    fun startAudioStreaming(socket: BluetoothSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = socket.inputStream
                val outputStream = socket.outputStream
                val buffer = ByteArray(1024)

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 8000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer.size
                )
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL, 8000,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer.size,
                    AudioTrack.MODE_STREAM
                )

                audioRecord.startRecording()
                audioTrack.play()

                // Receiving and Playing Audio
                launch {
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            audioTrack.write(buffer, 0, bytesRead)
                        }
                    }
                }

                // Recording and Sending Audio
                launch {
                    while (true) {
                        val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }

}
