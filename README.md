# 🌍 Spatial Diary

<img width="261" height="278" alt="Banner 3" src="https://github.com/user-attachments/assets/cde212fd-17ad-4265-ad5b-70d836558f8c" />

> Turn your Meta Ray-Ban footage into explorable 3D worlds — a memory you don't just keep, you walk back into.

Spatial Diary is an open-source Android app that lets you record first-person POV video using your **Meta Ray-Ban glasses**, and converts it into a fully walkable **3D Gaussian Splat world** using the **World Labs Marble API** — rendered live on your phone through a custom **SparkJS WebView**.

---

## How It Works

```
Android App (Spatial Diary)
        ↓
Meta Ray-Bans (POV Video)
        ↓
World Labs Marble API
(3D Gaussian Splat generation)
        ↓
SparkJS Web Viewer (WebView)
(Walkable 3D World on your phone)
```

1. Wear your Meta Ray-Bans and record a first-person video
2. The video is transferred to the Android app via the **Meta Wearables Developer Toolkit**
3. Tap **Convert to 3D** — the video is uploaded to World Labs and processed into a `.spz` Gaussian splat
4. Once ready, the app loads your world in a custom **SparkJS** WebView renderer
5. Walk through it, look around, relive it exactly as you lived it

---

## Screenshots

<img width="893" height="550" alt="Screenshots - Spatial Diary" src="https://github.com/user-attachments/assets/4adc995d-dcec-4880-a6c6-5c2a70572b62" />


## 📸 Video

https://www.linkedin.com/posts/rangesh06_spatial-diary-turn-your-videos-into-worlds-activity-7442932121264013313-kDGz?utm_source=share&utm_medium=member_desktop&rcm=ACoAACvOvZQB0-25fwwH4IcOOhOgt-tpp3HTWPQ

Check this demo to understand the core functionality of the product.

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Android App | Kotlin + Jetpack Compose |
| Wearable Integration | Meta Wearables Developer Toolkit |
| 3D World Generation | World Labs Marble 0.1 Plus |
| 3D Renderer | SparkJS (Gaussian Splatting) |
| WebView | Android WebView + AndroidView (Compose) |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android device running API 26+
- Meta Ray-Ban glasses (or use the mock device kit for testing)
- A [World Labs](https://platform.worldlabs.ai) account with API credits
- A GitHub account (for Meta Wearables Developer Toolkit access)

---

## 🔑 Setup — `local.properties`

This project requires two API credentials stored in your `local.properties` file. This file is **gitignored** and should never be committed to version control.

Open or create `local.properties` in the root of the project and add:

```properties
sdk.dir=/path/to/your/android/sdk
github_token=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
worldlabsAPI=YOUR_WORLD_LABS_API_KEY
```

### `github_token`

Required to access the **Meta Wearables Developer Toolkit** which is distributed as a private GitHub Packages dependency.

1. Go to [GitHub Settings → Developer Settings → Personal Access Tokens](https://github.com/settings/tokens)
2. Generate a new token (classic) with the `read:packages` scope
3. Paste it as `github_token` in `local.properties`

📖 Full setup guide: [Meta Wearables Developer Toolkit Docs](https://wearables.developer.meta.com/docs/develop)

---

### `worldlabsAPI`

Required to call the World Labs Marble API for 3D world generation.

1. Sign in at [platform.worldlabs.ai](https://platform.worldlabs.ai)
2. Add a payment method and purchase credits
3. Generate an API key from the [API Keys page](https://platform.worldlabs.ai/api-keys)
4. Paste it as `worldlabsAPI` in `local.properties`

📖 Full API reference: [World Labs API Docs](https://docs.worldlabs.ai/api)


---

## 🌐 Splat Viewer

The 3D world is rendered inside an Android WebView using a custom **SparkJS** Gaussian splatting viewer hosted on GitHub Pages.

**Viewer repo:** [github.com/rangesh06/splat-viewer](https://github.com/rangesh06/splat-viewer)

The viewer accepts a `.spz` file URL as a query parameter:

```
https://rangesh06.github.io/splat-viewer/?url=YOUR_SPZ_URL
```

You can fork this repo and host your own viewer on GitHub Pages if you want to customise the rendering experience.

---

## 💡 Tips for Developers

### Use `Marble 0.1-mini` for Testing

World Labs offers two models:

| Model | Speed | Cost | Use For |
|---|---|---|---|
| `Marble 0.1-plus` | ~5 minutes | Higher | Production / final quality |
| `Marble 0.1-mini` | ~30-45 seconds | Lower | Development / testing |

**During development, always use `Marble 0.1-mini`** to save time and credits. Switch to `Marble 0.1-plus` only when testing final output quality.

In `WorldLabsRepository.kt`, change the model field:

```kotlin
// For testing
"model": "Marble 0.1-mini"

// For production
"model": "Marble 0.1-plus"
```

---

### Splat Resolution — Use `100k` or `500k` if `full_res` Fails

World Labs generates splats in three resolutions:

| Resolution | Gaussians | File Size | Recommended For |
|---|---|---|---|
| `100k` | 100,000 | Smallest | Mobile / fast loading |
| `500k` | 500,000 | Medium | Good balance |
| `full_res` | Maximum | Largest | Desktop only |

On mobile, `full_res` can cause memory crashes or very slow loading. Start with `100k` during development and move to `500k` if you need more detail.

This project uses "full_res" for maximum quality output.

In `WorldLabsRepository.kt`:

```kotlin
// Recommended for mobile
splats.getString("100k")

// If you need more detail
splats.getString("500k")

// Avoid on mobile
splats.getString("full_res")
```

## 📋 API Flow

```
1. POST /media-assets:prepare_upload   → get signed URL + media asset ID
2. PUT  <signed_url>                   → upload video file
3. POST /worlds:generate               → trigger world generation
4. GET  /operations/{id}               → poll every 30s until done (~5 mins)
5. Extract spz_urls.100k               → load in SparkJS WebView

---

## 🤝 Contributing

Contributions are welcome. Please open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create your feature branch 
3. Commit your changes 
4. Push to the branch 
5. Open a Pull Request

---

## 📚 References

| Resource | Link |
|---|---|
| Meta Wearables Developer Toolkit | [wearables.developer.meta.com/docs/develop](https://wearables.developer.meta.com/docs/develop) |
| World Labs API Docs | [docs.worldlabs.ai/api](https://docs.worldlabs.ai/api) |
| SparkJS Renderer | [sparkjs.dev](https://sparkjs.dev) |
| Splat Viewer (this project) | [github.com/rangesh06/splat-viewer](https://github.com/rangesh06/splat-viewer) |
| SPZ Format (Niantic) | [github.com/nianticlabs/spz](https://github.com/nianticlabs/spz) |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## ⚠️ Disclaimer

- World Labs API usage incurs costs. Use `Marble 0.1-mini` and `100k` splats during development to minimise spend.
- Meta Ray-Ban integration requires acceptance of Meta's developer terms.
- This project is not affiliated with or endorsed by Meta or World Labs.

---

Built by [@rangesh06](https://github.com/rangesh06)
