package com.adhdcompanion.focus.data.remote

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null
)

data class GeminiSpeechConfig(
    val voiceConfig: GeminiVoiceConfig? = null
)

data class GeminiVoiceConfig(
    val prebuiltVoiceConfig: GeminiPrebuiltVoiceConfig? = null
)

data class GeminiPrebuiltVoiceConfig(
    val voiceName: String = "Kore"
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 1024,
    val responseMimeType: String? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: GeminiSpeechConfig? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiTtsResponse(
    val candidates: List<GeminiTtsCandidate>?
)

data class GeminiTtsCandidate(
    val content: GeminiTtsContent?
)

data class GeminiTtsContent(
    val parts: List<GeminiTtsPart>?
)

data class GeminiTtsPart(
    val inlineData: GeminiInlineData?
)

data class GeminiInlineData(
    val mimeType: String?,
    val data: String?
)
