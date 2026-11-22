# FilesC8

Prehľadný a farebný správca súborov pre Android (Kotlin + Jetpack Compose). Aplikácia je dvojjazyčná, prepína sa vlajkami SK / EN.

## Funkcie
- **Prehľad**: využitie úložiska (kruhový ukazovateľ a farebný pruh podľa kategórií), vyhľadávanie súborov, 8 kategórií s počtom súborov a veľkosťou, nedávne súbory, zbierky (Obľúbené, Veľké súbory, Úložisko)
- **Kategórie**: Stiahnuté, Obrázky, Videá, Zvukové, Dokumenty, Aplikácie, Archívy a APK, Snímky obrazovky. Súbory sa dajú filtrovať podľa priečinka, triediť a označiť naraz na zdieľanie, pridanie do obľúbených alebo vymazanie
- **Prehliadať**: priečinky v internom úložisku aj na SD karte, cesta k priečinku, nový priečinok, skryté súbory
- **Vyčistiť**: veľké súbory, duplicity, staré snímky obrazovky, APK inštalačky, staré stiahnuté súbory
- **Aplikácie**: zoznam nainštalovaných aplikácií podľa veľkosti, odinštalovanie

## Inštalácia do telefónu
Každý push do `main` spustí GitHub Actions. Tie zostavia APK a zverejnia ho v **Releases**. V telefóne otvor stránku Releases, stiahni `FilesC8-N-debug.apk` a nainštaluj ho.

## Google Play
Ak v repozitári nastavíš secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` a `KEY_PASSWORD`, workflow zostaví aj podpísaný `.aab` pre Google Play Console.

Balík: `sk.vespal.filesc8` · © VESPAL s. r. o.
