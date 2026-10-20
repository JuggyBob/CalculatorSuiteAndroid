package com.calcsuite.ai

/**
 * The AI providers the app can talk to.
 *
 * Every provider is described by where its requests go, how it is
 * authenticated, and how its request and response are shaped. The ten
 * providers fall into three families:
 *
 *  - **OpenAI-compatible** ([OPENAI], [OPENROUTER], [DEEPSEEK], [GROQ],
 *    [MISTRAL], [XAI], [TOGETHER], [FIREWORKS]): the same
 *    `/chat/completions` shape, with the image in an `image_url` block and
 *    the reply under `choices[0].message.content`.
 *  - **[ANTHROPIC]**: the Messages API, with the image in an `image` block
 *    carrying a `source`, and the reply under `content[0].text`.
 *  - **[GEMINI]**: the Generative Language API, with the image as
 *    `inline_data`, and the reply under `candidates[0].content.parts[0].text`.
 *
 * The model name is always entered by the user, so a new model can be used
 * without a new release.
 */
enum class AiProvider(
    val id: String,
    val label: String,
    /** The base address the requests are sent to. */
    val baseUrl: String,
    /** The shape of the requests and the replies. */
    val format: AiWireFormat,
    /** True when the provider's models are known to read images. */
    val supportsVision: Boolean,
    /** A note about which of the provider's models can read an image. */
    val visionNote: String,
) {
    OPENAI(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision needs a model such as gpt-4o or newer.",
    ),
    ANTHROPIC(
        id = "anthropic",
        label = "Anthropic",
        baseUrl = "https://api.anthropic.com/v1",
        format = AiWireFormat.ANTHROPIC_MESSAGES,
        supportsVision = true,
        visionNote = "All current Claude models read images.",
    ),
    GEMINI(
        id = "gemini",
        label = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        format = AiWireFormat.GEMINI_CONTENT,
        supportsVision = true,
        visionNote = "The Gemini Pro and Flash models read images.",
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision depends on the model chosen; many hosted models read images.",
    ),
    DEEPSEEK(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Only the Flash models read images; the Pro model is text only.",
    ),
    GROQ(
        id = "groq",
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision depends on the model chosen; some hosted models read images.",
    ),
    MISTRAL(
        id = "mistral",
        label = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision needs a Pixtral model; the other Mistral models are text only.",
    ),
    XAI(
        id = "xai",
        label = "xAI",
        baseUrl = "https://api.x.ai/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision depends on the model chosen; the Grok vision models read images.",
    ),
    TOGETHER(
        id = "together",
        label = "Together AI",
        baseUrl = "https://api.together.xyz/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision depends on the model chosen; many hosted models read images.",
    ),
    FIREWORKS(
        id = "fireworks",
        label = "Fireworks AI",
        baseUrl = "https://api.fireworks.ai/inference/v1",
        format = AiWireFormat.OPENAI_CHAT,
        supportsVision = true,
        visionNote = "Vision depends on the model chosen; many hosted models read images.",
    );

    /** The URL for the model, for the providers whose URL carries the name. */
    fun urlFor(model: String): String = when (format) {
        AiWireFormat.OPENAI_CHAT -> "$baseUrl/chat/completions"
        AiWireFormat.ANTHROPIC_MESSAGES -> "$baseUrl/messages"
        AiWireFormat.GEMINI_CONTENT -> "$baseUrl/models/$model:generateContent"
    }

    companion object {
        fun byId(id: String): AiProvider? = entries.firstOrNull { it.id == id }
    }
}

/** The shape of a provider's requests and replies. */
enum class AiWireFormat {
    /** The OpenAI chat completions shape. */
    OPENAI_CHAT,

    /** The Anthropic messages shape. */
    ANTHROPIC_MESSAGES,

    /** The Google Gemini generateContent shape. */
    GEMINI_CONTENT,
}
