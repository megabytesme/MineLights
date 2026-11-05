# Building MineLights

NeoForge 1.21.11 and later source preparation gives the decompiler a maximum heap of 12 GB.
Its large generated block initializer can exceed NeoGradle's default 4 GB limit.
This is a maximum rather than a reservation, but the preparation step may require
substantial available memory. To choose another limit, pass for example
`-Pneogradle.subsystems.decompiler.maxMemory=8g` to Gradle. If source patching
fails because `Blocks.java` could not be decompiled, raise the limit and retry.
