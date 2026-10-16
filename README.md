# Elite Android

Remake para Android do clássico **Elite** (1984 / MSX), com fidelidade aos algoritmos
originais e visual wireframe neon. Kotlin + LibGDX, com UI Android sobreposta.

Referências: *Mark Moxon's Elite Disassembly* e *Text Elite* (Ian Bell).

## Estrutura

```
core/     Kotlin/JVM puro — lógica do jogo + LibGDX core (testável sem Android SDK)
  galaxy/   Seed (6 bytes), GalaxyGenerator, PlanetSystem, GoatSoup (descrições)
android/  App Android — AndroidLauncher (LibGDX view + overlays Android Views)
```

## Etapas

1. ✅ Estrutura do projeto + gerador de galáxias Text Elite
2. ⏳ Renderização 3D wireframe neon + Cobra Mk III e estação Coriolis
3. ⏳ Física de voo, câmeras e docking
4. ⏳ Scanner 3D e HUD
5. ⏳ Controles touch
6. ⏳ Economia, mercado e hiperespaço

## Build

Requisitos: JDK 17+, Android SDK (API 35).

```bash
./gradlew :core:test          # testes do gerador (não precisa de Android SDK)
./gradlew :android:installDebug
```

O módulo `:android` só é incluído quando há `ANDROID_HOME`/`ANDROID_SDK_ROOT` ou
`sdk.dir` em `local.properties`.

## Gerador de galáxias (Etapa 1)

- Semente inicial `5A4A 0248 B753`; `tweak()` = Fibonacci de 16 bits (4× por sistema).
- Galáxia seguinte: cada byte da semente rotacionado 1 bit à esquerda (8 galáxias, a 9ª = 1ª).
- Atributos de `makesystem()`: posição, economia, governo, tech level, população,
  produtividade, raio, nome (pares de letras) e descrição "goat soup".
- Espécie dominante conforme rotina TT25 do BBC Elite.
- `distance()` em décimos de ano-luz, idêntica ao Text Elite (inclusive `dy²/4` inteiro).

Valores verificados em teste: Lave (20,173), Rich Agri, Dictatorship, TL 5, 2.5 bi,
7000 M CR, raio 4116, *"Lave is most famous for its vast rain forests and the Lavian tree grub."*
