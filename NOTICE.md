# Licensing

Beyond and Tabs is free software: you can redistribute it and/or modify it under the terms of the GNU General
Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option)
any later version. See `LICENSE`.

## Third-party code

Parts of the game logic may be ported from [Beyond All Reason](https://github.com/beyond-all-reason/beyond-all-reason)
(GPL v2). Ported code keeps its original authors' copyright and licence; each ported file says what it was
ported from.

## Assets

No Beyond All Reason models, textures, animations, sounds, icons or unit pictures are used (those are under
licences that forbid derivative works). All unit skins, building plans and effects in this mod are original.
Totally Accurate Battle Simulator is an inspiration only; no TABS assets are included.

## Announcer voice

The spoken alerts in `bar/src/main/resources/assets/reignofnether/sounds/announcer/` are synthetic speech generated
by [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) (hexgrad), voice `af_heart`, released under the
**Apache License 2.0** (model card `license: apache-2.0`; the generation workflow re-checks this before every run and
refuses to synthesise if it changes). The model card states it was trained only on permissive/non-copyrighted audio
(public domain, Apache/MIT-licensed and synthetic audio), and Apache-2.0 places no restriction on distributing the
generated audio, commercially or otherwise. The voice is one of Kokoro's stock voices; it is not modelled on, cloned
from or meant to imitate any real person or any other game's narrator, and no third-party game audio was used.
Post-processing (pitch, filtering, chorus, reverb, chime) is done with ffmpeg in `.github/workflows/voice.yml`; the
two-tone chime is generated from sine waves. The lines themselves are in `tools/voice_lines.txt`.

## Reign of Nether

`bar/` is a fork of [Reign of Nether](https://github.com/SoLegendary/reignofnether) by SoLegendary and contributors,
licensed under the GNU GPL v3. The fork (and therefore the combined mod built from it) is distributed under GPL v3.
The Essential partner-mod integration bundled with upstream has been removed. Upstream sound files whose origin is not
documented are kept from upstream as distributed there; they will be replaced before any public release.
