# VoidCraft Launcher — MojoLauncher source and license notice

This branch builds from the public [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) repository, branch **v3_openjdk**, pinned to commit **8ffba8f4ea0b9466854aa9f7a375c77b4fb96bdd**.

MojoLauncher states that its code is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0). The build pipeline preserves a copy of the upstream LICENSE as VOIDCRAFT_MOJO_LICENSE.txt in the build workspace and records the upstream revision in build information.

The initial VoidCraft layer changes only the Android application ID/provider authorities, app/version labels, crash label, original VC launcher icon, and night-violet palette. The Java namespace remains git.artdeell.mojo so upstream activity names continue to resolve. Launcher implementation, Java runtime integration, native libraries, renderer backends, download flows, and account flows are retained from the pinned source.

MojoLauncher includes separately licensed third-party components and git submodules. Their terms must be checked and retained individually before redistribution. The LGPL notice alone is not a complete software bill of materials or a declaration that every dependency permits every distribution method. Do not reuse upstream private signing keys; this CI build uses the debug variant.

Recheck upstream branch and license status before release. This is an engineering provenance record, not legal advice.
