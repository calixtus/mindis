/// Update check: asks the release server whether a newer MinDis exists,
/// downloads the installer for this platform and hands it to the operating
/// system (docs/adr/010-auto-update.md). Nothing here replaces files inside the
/// running application - the jpackage installer does the installing.
@NullMarked
package org.mindis.core.update;

import org.jspecify.annotations.NullMarked;
