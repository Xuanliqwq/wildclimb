# Building Wild Climb

## Requirements

- **JDK 21** (the build script also works on newer JDKs; Gradle will pick a toolchain)
- **Paraglider** (the stamina system this mod is built on)

## Steps

1. Clone this repository.
2. Download the Paraglider jar for Minecraft **1.21.1 / NeoForge**:
   <https://www.curseforge.com/minecraft/mc-mods/paragliders/files>
3. Put the downloaded jar into the `libs/` folder and make sure the file name matches
   the `paraglider_jar` entry in `gradle.properties`
   (currently `Paraglider-neoforge-21.1.5.jar`). Rename the file if needed.
4. Build:

   ```bash
   # Windows
   gradlew.bat build

   # macOS / Linux
   ./gradlew build
   ```

5. The finished mod jar is in `build/libs/wildclimb-<version>.jar`.

## Notes

- To build with a Paraglider JAR outside the checkout, pass "-PparagliderPath=/absolute/path/to/Paraglider.jar" to Gradle.

- `libs/*.jar` is **not** committed on purpose: Paraglider is licensed under **GPLv3**
  and is distributed by its own author. Please download it from the link above instead
  of bundling it with this repository.
- Player Animation Library is resolved automatically from Maven by Gradle.
- The first build downloads Gradle, NeoForge and Minecraft dependencies, so it takes a
  few minutes; later builds are much faster.

## License

This project is licensed under the **GNU General Public License v3 (GPLv3)**.
See [LICENSE](LICENSE) for the full text.
