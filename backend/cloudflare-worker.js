const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const DEFAULT_MODEL = "gemini-2.5-flash";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true });
    }

    if (request.method === "POST" && url.pathname === "/v1/class-suggestions") {
      return handleClassSuggestions(request, env);
    }

    return json({ error: "Not found" }, 404);
  }
};

async function handleClassSuggestions(request, env) {
  try {
    if (!env.GEMINI_API_KEY) {
      return json({ error: "Gemini backend is not configured" }, 503);
    }

    const body = await request.json();
    const imageBase64 = body?.imageBase64;
    if (typeof imageBase64 !== "string" || imageBase64.length === 0) {
      return json({ error: "imageBase64 is required" }, 400);
    }
    if (decodedByteLength(imageBase64) > MAX_IMAGE_BYTES) {
      return json({ error: "Image is larger than 10 MB" }, 413);
    }

    const model = env.GEMINI_MODEL || DEFAULT_MODEL;
    const geminiResponse = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(geminiRequest(imageBase64))
      }
    );

    const payload = await geminiResponse.json();
    if (!geminiResponse.ok) {
      console.error("Gemini request failed", geminiResponse.status);
      return json({ error: "Gemini provider request failed" }, 502);
    }

    const text = payload.candidates?.[0]?.content?.parts?.[0]?.text;
    if (typeof text !== "string") {
      return json({ error: "Gemini returned no structured suggestion" }, 502);
    }

    const parsed = JSON.parse(text);
    return json({ classes: validateClasses(parsed.classes) });
  } catch (error) {
    console.error("Class suggestion request failed", error);
    return json({ error: "Class suggestion request failed" }, 502);
  }
}

function geminiRequest(imageBase64) {
  return {
    contents: [{
      parts: [
        { inline_data: { mime_type: "image/jpeg", data: imageBase64 } },
        {
          text: "Suggest visual classes for the decoration pieces in this image. Do not infer or suggest print quantities. Return only JSON matching {\"classes\":[{\"label\":\"string\",\"cropIndexes\":[],\"confidence\":0.0}]}. Use concise labels such as hats, figures, or letters. cropIndexes may be empty because crop review is manual."
        }
      ]
    }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: {
        type: "OBJECT",
        properties: {
          classes: {
            type: "ARRAY",
            items: {
              type: "OBJECT",
              properties: {
                label: { type: "STRING" },
                cropIndexes: { type: "ARRAY", items: { type: "INTEGER" } },
                confidence: { type: "NUMBER" }
              },
              required: ["label", "cropIndexes"]
            }
          }
        },
        required: ["classes"]
      }
    }
  };
}

function validateClasses(value) {
  if (!Array.isArray(value)) throw new Error("classes must be an array");
  return value
    .map((item) => ({
      label: typeof item?.label === "string" ? item.label.trim() : "",
      cropIndexes: Array.isArray(item?.cropIndexes)
        ? item.cropIndexes.filter((index) => Number.isInteger(index) && index >= 0)
        : [],
      confidence: typeof item?.confidence === "number" ? item.confidence : undefined
    }))
    .filter((item) => item.label.length > 0)
    .slice(0, 30);
}

function decodedByteLength(value) {
  return Math.floor((value.length * 3) / 4) - (value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0);
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...corsHeaders(), "Content-Type": "application/json" }
  });
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
  };
}
