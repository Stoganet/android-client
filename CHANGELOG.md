# Changelog

## [0.3.0](https://github.com/Stoganet/android-client/compare/v0.2.0...v0.3.0) (2026-10-17)


### Features

* **tv:** act on key presses while player controls are hidden ([46ea032](https://github.com/Stoganet/android-client/commit/46ea0322f0ff2d218857ce507eee1fec50a03633))
* **tv:** add a next episode button to the player ([7562f88](https://github.com/Stoganet/android-client/commit/7562f8813f439cb04308f299cabf1d9c4a853cc2))
* **tv:** add next episode button and title line to the player ([a7454b3](https://github.com/Stoganet/android-client/commit/a7454b32070f5d589a87a4768f5f5a22725128bf))
* **tv:** redesign player controls with icons and a seek bar ([42bd50f](https://github.com/Stoganet/android-client/commit/42bd50fa9bc49bf8700ffc9da566724ab67fbbb9))
* **tv:** redesign player controls with icons and a seek bar ([550a60b](https://github.com/Stoganet/android-client/commit/550a60be03f932e1e749e275372f35e9f488834b))
* **tv:** show a spinner while the player is buffering ([4c3583d](https://github.com/Stoganet/android-client/commit/4c3583dbbd5127dc67a8b58084251e9df3e9bc2d))
* **tv:** show the title and episode line in the player controls ([cd05ec9](https://github.com/Stoganet/android-client/commit/cd05ec9bd36976401c85657b9e02d05d715f6fcd))


### Bug Fixes

* **tv:** base seek speed on key hold and ignore held OK ([b38d81a](https://github.com/Stoganet/android-client/commit/b38d81a86fa3619bc9488d20c98342e0472848eb))
* **tv:** don't restart playback after leaving during a token refresh ([6960297](https://github.com/Stoganet/android-client/commit/69602972dfeee31ac6de0d76180a4cecd474bbeb))
* **tv:** drop the spinner left by a cancelled search ([70fc5c6](https://github.com/Stoganet/android-client/commit/70fc5c6772d42d31b9d0c2a79c0a2606f93b9b78))
* **tv:** harden leaving and restarting the player ([2dedb87](https://github.com/Stoganet/android-client/commit/2dedb8722465826d779d34d6aefb1c0d2d367d95))
* **tv:** keep a finished episode watched when leaving the player ([e438881](https://github.com/Stoganet/android-client/commit/e438881c590ff33a82f5fc89d5c87a3d618d0ed0))
* **tv:** keep a pending seek when leaving the player ([efc1ab6](https://github.com/Stoganet/android-client/commit/efc1ab6ebb07de3962330b3a7c77f1647834f811))
* **tv:** keep forward seeks inside the known video length ([481cac3](https://github.com/Stoganet/android-client/commit/481cac31060ac4a93676f1717488447091b835d5))
* **tv:** refresh Detail's resume point after playback ([fada1b0](https://github.com/Stoganet/android-client/commit/fada1b023850198f2a53882097f9dd4da51f1493))
* **tv:** sign-in flash, Detail resume point and Next in the same player ([ccd4106](https://github.com/Stoganet/android-client/commit/ccd41068cc14dc991e2383b01e8fd61278ade1e3))
* **tv:** start watched episodes from the beginning ([7941530](https://github.com/Stoganet/android-client/commit/79415301280605ed31643801c13e129ac875a05f))
* **tv:** stop the login screen flashing on launch when signed in ([dcc01c0](https://github.com/Stoganet/android-client/commit/dcc01c097c71b6387dc8b214c54e1a6c71fe4954))


### Performance Improvements

* **tv:** swap episodes inside the same player on Next ([1b200c7](https://github.com/Stoganet/android-client/commit/1b200c7fdab586b0e27ac89a1fd6a0c26f22fa91))

## [0.2.0](https://github.com/Stoganet/android-client/compare/v0.1.1...v0.2.0) (2026-10-11)


### Features

* **tv:** add focus-driven hero and pinned rows to home ([e1e710f](https://github.com/Stoganet/android-client/commit/e1e710f37eeb8b3386d53d2c13a1ce08163d01eb))
* **tv:** enlarge home cards and give detail overview more lines ([071b005](https://github.com/Stoganet/android-client/commit/071b0051bb9e0cd0dff6f2d1df05022289931787))
* **tv:** map hero text and card images into home items ([e31f2b9](https://github.com/Stoganet/android-client/commit/e31f2b90ba6f3376adce07216951e6072c3f3f7d))
* **tv:** redesign home with a focus-driven hero and 16:9 cards ([3a28cc3](https://github.com/Stoganet/android-client/commit/3a28cc36fd0517d6202d505bc9160299fccfb92c))
* **tv:** show home rows as 16:9 backdrop cards ([7bc0af9](https://github.com/Stoganet/android-client/commit/7bc0af9d32f6c5235e086568fd3c8de2115dba9d))


### Bug Fixes

* **tv:** pin home rows on any focus and hide unknown years ([709a5ef](https://github.com/Stoganet/android-client/commit/709a5ef2d1e49ee937cff1fe968ceac7a3b9ab06))

## [0.1.1](https://github.com/Stoganet/android-client/compare/v0.1.0...v0.1.1) (2026-10-08)


### Bug Fixes

* **core:** keep proto classes in R8, log Ktor to Logcat, allow debug API_BASE_URL override ([b346214](https://github.com/Stoganet/android-client/commit/b3462146f68fdd91d585fde2bc751d19b5c45376))
* **tv:** handle items with missing poster and metadata ([c94dc04](https://github.com/Stoganet/android-client/commit/c94dc044f233e8075b135bba9b8135d7a440a93a))
* **tv:** keep focus on the clicked card after returning from details ([6329405](https://github.com/Stoganet/android-client/commit/6329405799398dbe7711c2ae4060a086ceea63cc))
* **tv:** move focus to the drawer on back before leaving a screen ([1121219](https://github.com/Stoganet/android-client/commit/11212192639211ada6ec490d17e8b6728bf88eef))
* **tv:** play finished shows from the first episode ([2d9bd03](https://github.com/Stoganet/android-client/commit/2d9bd03232f0047561a54158211bc6bfc298e0de))
* **tv:** show Play unless an item is partially watched ([761291e](https://github.com/Stoganet/android-client/commit/761291e564f1d494baa99f40d8be2df2c361da26))
* **tv:** use a short fade for screen transitions ([ebd8b5c](https://github.com/Stoganet/android-client/commit/ebd8b5ca80f09d9c83501efaa5e312d05faafa3a))
