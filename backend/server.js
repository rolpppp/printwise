import "dotenv/config";
import express from "express";

const app = express();
const port = Number(process.env.PORT || 8080);
const model = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const maxImageBytes = 10 * 1024 * 1024;

app.disable("x-powered-by");
app.use(express.json({ limit: "14mb" }));

app.get("/health", (_request, response) => {
  response.json({ ok: true });
});

app.post("/v1/class-suggestions", async (request, response) => {
  try {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return response.status(503).json({ error: "Gemini backend is not configured" });
    }

    const imageBase64 = request.body?.imageBase64;
    if (typeof imageBase64 !== "string" || imageBase64.length === 0) {
      return response.status(400).json({ error: "imageBase64 is required" });
    }
    if (Buffer.byteLength(imageBase64, "base64") > maxImageBytes) {
      return response.status(413).json({ error: "Image is larger than 10 MB" });
    }

    const geminiResponse = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: AbortSignal.timeout(30_000),
        body: JSON.stringify({
          contents: [{
            parts: [
              {
                inline_data: {
                  mime_type: "image/jpeg",
                  data: imageBase64
                }
              },
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
        })
      }
    );

    const payload = await geminiResponse.json();
    if (!geminiResponse.ok) {
      console.error("Gemini request failed", geminiResponse.status);
      return response.status(502).json({ error: "Gemini provider request failed" });
    }

    const text = payload.candidates?.[0]?.content?.parts?.[0]?.text;
    if (typeof text !== "string") {
      return response.status(502).json({ error: "Gemini returned no structured suggestion" });
    }

    const parsed = JSON.parse(text);
    const classes = validateClasses(parsed.classes);
    return response.json({ classes });
  } catch (error) {
    console.error("Class suggestion request failed", error);
    return response.status(502).json({ error: "Class suggestion request failed" });
  }
});

function validateClasses(value) {
  if (!Array.isArray(value)) {
    throw new Error("classes must be an array");
  }
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

app.listen(port, "0.0.0.0", () => {
  console.log(`Printwise Gemini backend listening on ${port}`);
});
