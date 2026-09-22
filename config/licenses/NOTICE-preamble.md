# Third-party notices

MinDis is Copyright the MinDis contributors and is distributed under the Apache
License, Version 2.0 (see the `LICENSE` file, or
<https://www.apache.org/licenses/LICENSE-2.0>).

Everything below is third-party material distributed with MinDis. The module
list and the NOTICE sections are generated from the dependencies actually
resolved for the packaged application, so they cannot drift from what ships.

## Java runtime and JavaFX

The installer bundles a Java runtime image built from OpenJDK, including the
JavaFX (OpenJFX) modules. OpenJDK and OpenJFX are distributed under the GNU
General Public License, version 2, **with the Classpath Exception**. That
exception explicitly permits linking independent modules - MinDis and the
libraries listed below - and distributing the result under their own terms; it
places no license conditions on MinDis's own code.

Corresponding sources for the bundled runtime are published by their projects:

- OpenJDK: <https://github.com/openjdk/jdk>, builds by Eclipse Adoptium
  (<https://github.com/adoptium/temurin-build>)
- OpenJFX: <https://github.com/openjdk/jfx>

The same license and exception apply to `io.smallrye.classfile:jdk-classfile-backport`,
a backport of the JDK's own class-file API, which appears in the module list
below.

## DejaVu fonts

MinDis embeds DejaVu Sans (regular and bold) into exported PDF files. The fonts
are distributed under the Bitstream Vera Fonts license with the Arev fonts
additions; the full text ships with the application in
`org/mindis/core/export/font/DejaVuFonts-LICENSE.txt`.

> Fonts are (c) Bitstream (see the license file). DejaVu changes are in public
> domain. Glyphs imported from Arev fonts are (c) Tavmjong Bah.
>
> Bitstream Vera is a trademark of Bitstream, Inc.
