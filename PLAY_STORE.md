# Vydanie FilesC8 na Google Play (účet VESPAL s. r. o.)

Balík: **sk.vespal.filesc8** · Kategória: **Nástroje (Tools)** · Zadarmo · Bez reklám

## 1. Podpisový kľúč a GitHub Secrets

Kľúč `filesc8-upload.jks` už existuje. Do *Settings → Secrets and variables → Actions* pridaj
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `filesc8`) a `KEY_PASSWORD`.
Každý ďalší push potom vytvorí aj súbor `FilesC8-N.aab` (v Releases).

## 2. Vytvorenie aplikácie v Play Console

*Všetky aplikácie → Vytvoriť aplikáciu*
- Názov: **FilesC8**
- Predvolený jazyk: slovenčina (sk-SK)
- Aplikácia / Zadarmo, a potvrdiť vyhlásenia

## 3. Obsah aplikácie (App content) – formuláre

| Formulár | Odpoveď |
|---|---|
| Zásady ochrany súkromia | `https://github.com/sevcikjaroslav1-commits/FilesC8/blob/main/PRIVACY.md` |
| Reklamy | Nie |
| Prístup k aplikácii | Všetky funkcie sú dostupné bez prihlásenia |
| Hodnotenie obsahu | Kategória *Všetky ostatné typy aplikácií / Nástroje*, na všetky otázky **Nie** |
| Cieľová skupina | 13+ (13–15, 16–17, 18+), nie je určená deťom |
| Bezpečnosť údajov | Zhromažďuje alebo zdieľa údaje? **Nie** |
| Vládna / finančná / zdravotná aplikácia | Nie |

### ⚠️ Povolenie „Prístup ku všetkým súborom“ (MANAGE_EXTERNAL_STORAGE)

*App content → Povolenia → All files access*: tento formulár je **povinný**, inak Google aplikáciu zamietne.

- **Hlavný účel:** *File manager* (Správca súborov)
- **Popis použitia (EN):**
  > FilesC8 is a file manager. Its core feature is to browse, search, open, play, share, move and delete
  > any files the user has on the device storage and SD card (Downloads, Documents, archives, APK files,
  > folders created by other apps) and to find large, duplicate and old files to free up space.
  > This is not possible with the MediaStore or Storage Access Framework APIs alone, because they don't give
  > access to all folders and file types. Files never leave the device unless the user shares them.
- **Video:** krátka nahrávka (30–60 s, nahraj ju cez RecShotC8, nahraj na YouTube ako *Nezaradené*):
  spustenie → obrazovka „Povoliť prístup“ → zapnutie v nastaveniach → Prehľad → Stiahnuté → Prehliadať priečinky → Vyčistiť.

## 4. Záznam v obchode (Store listing)

**Názov:** FilesC8

**Krátky popis (SK, max. 80 znakov):**
Prehľadný a farebný správca súborov s prehrávačom, čistením a prenosom.

**Dlhý popis (SK):**
FilesC8 je prehľadný správca súborov s farebnými kategóriami, v ktorom všetko nájdeš na prvý pohľad.

• Prehľad úložiska na jednej obrazovke: koľko miesta zaberajú videá, fotky, aplikácie a ďalšie
• 8 farebných kategórií: Stiahnuté, Obrázky, Videá, Zvukové, Dokumenty, Aplikácie, Archívy a APK, Snímky obrazovky
• Podpriečinky prepneš ťuknutím alebo potiahnutím prstom
• Vstavaný prehliadač fotiek so zoomom a prehrávač videí
• Hudobný prehrávač so zoznamom skladieb, náhodným poradím, opakovaním a mini lištou
• Prehratie hudby v Bluetooth reproduktore alebo slúchadlách
• Zdieľanie aj prenos do blízkeho telefónu či PC cez Wi‑Fi (Quick Share) alebo Bluetooth
• Vyčistenie: veľké súbory, duplicity, staré snímky obrazovky, APK inštalačky, staré stiahnuté súbory
• Prehliadanie priečinkov v telefóne aj na SD karte, nový priečinok, skryté súbory
• Vyhľadávanie, obľúbené súbory, hromadné označenie, zdieľanie a mazanie
• Slovenčina aj angličtina, prepínanie vlajkou
• Bez reklám, bez registrácie, nič sa neodosiela na internet

**Short description (EN):**
A clear, colourful file manager with a media player, cleaner and file transfer.

**Full description (EN):**
FilesC8 is a clean, colourful file manager that shows everything at a glance.

• Storage overview on one screen: see what takes up space
• 8 colour-coded categories: Downloads, Images, Videos, Audio, Documents, Apps, Archives & APK, Screenshots
• Switch subfolders by tapping or swiping
• Built-in photo viewer with zoom and video player
• Music player with playlist, shuffle, repeat and mini player
• Play music on a Bluetooth speaker or headphones
• Share or send files to a nearby phone or PC over Wi‑Fi (Quick Share) or Bluetooth
• Clean up: large files, duplicates, old screenshots, APK installers, old downloads
• Browse folders on the phone and SD card, create folders, show hidden files
• Search, favourites, multi-select, share and delete
• Slovak and English
• No ads, no sign-up, nothing is uploaded

**Grafika** (priečinok `store/`):
- Ikona 512×512: `store/FilesC8_ikona_512.png`
- Hlavný obrázok 1024×500: `store/FilesC8_titulny_1024x500.png`
- Snímky obrazovky telefónu (min. 2, odporúčané 4–8). Sprav ich priamo v telefóne: Prehľad, Videá, prehliadač fotky, Hudba, Prehliadať, Vyčistiť.

**Kontakt:** e-mail vývojára (povinný), web voliteľne.

## 5. Vydanie

*Produkcia → Vytvoriť nové vydanie* → nahraj `FilesC8-N.aab` → poznámky k vydaniu:
`Prvé vydanie FilesC8 – prehľadný správca súborov.` → *Skontrolovať* → *Spustiť zavádzanie*.

Posúdenie zvyčajne trvá 1–7 dní. Pri aplikáciách s prístupom ku všetkým súborom môže trvať dlhšie.

Každý ďalší push do `main` vytvorí nový build s vyšším číslom verzie (versionCode = číslo behu GitHub Actions).
