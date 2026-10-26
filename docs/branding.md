# Identità Jarvis Files approvata

Applicazione del concept approvato nella conversazione del 26 ottobre 2026: cartella avorio/arancio, nucleo blu a tre raggi, wordmark Jarvis con puntino arancio e FILES spaziato sotto. Non sono cambiate le funzioni dell'app o la configurazione AI.

| Risorsa | Uso |
| --- | --- |
| `app/src/main/res/drawable-nodpi/jarvis_folder_brand.webp` | Cartella del marchio e foreground dell'icona Android |
| `app/src/main/res/drawable-nodpi/jarvis_sphere_brand.webp` | Sfera grande nella Home, benvenuto e ascolto |
| `app/src/main/res/drawable/jarvis_symbol_brand.xml` | Simbolo vettoriale compatto per barra e chat |
| `app/src/main/res/drawable/jarvis_wordmark_brand.xml` | Scritta vettoriale Jarvis / FILES |
| `app/src/main/res/drawable/jarvis_launcher_layer.xml` | Foreground adattivo centrato, senza riquadro incorporato |
| `app/src/main/res/drawable/jarvis_launcher_monochrome.xml` | Icone a tema Android 13+ |

Il wordmark deriva da Inter già incluso nel progetto. Si rigenera con `python scripts/build_brand_wordmark.py` (richiede fontTools). La parola è convertita in tracciati: non dipende dai caratteri installati sul telefono.

Le due risorse bitmap sono state preparate con **Imagegen integrato**, usando il concept approvato come riferimento. Sfondo alpha trasparente. Codifica finale WebP **senza perdita**, stessa risoluzione dei master. Non sono state ritagliate dalle schermate del prototipo.

## Prompt cartella

Extract and faithfully reproduce the APPROVED LARGE FOLDER APP ICON from the center-right of this reference design board as one standalone production asset for Android. This is not a redesign. The output must contain ONLY that exact folder emblem with transparent background: orange rear tab, smooth ivory front folder face, the deep navy circular core centered in front, single thin luminous orange rim, and the distinctive three-pronged icy-blue/white Jarvis glyph with three rays pointing upper-left, upper-right and downward. Preserve the approved proportions, colors, soft subtle 3D shading and clear simple silhouette. Full folder is front-facing, flat camera, no tilted perspective. No presentation board, no phone, no captions, no wordmark, no white or grey background tile behind the folder, no Android rounded-square container, no duplicate icons, no drop shadow beyond a very subtle edge contact shading. Folder itself has rounded geometry as in the approved reference. Centered in a square high-resolution image, folder fills 88 percent of image width, complete uncropped tabs with balanced transparent margins all around. Genuine alpha transparency outside folder. Maintain exact identity from reference and very crisp clean silhouette suitable for 48 dp launcher icon and in-app logo. No additional orbit lines, no added metallic rings or bolts. Deliver a single isolated folder asset.

## Prompt sfera

Extract and faithfully reproduce the APPROVED JARVIS SPHERE from the upper-right of the phone and lower 'Jarvis nella Home' study in the reference design board. One standalone production app asset, square high resolution with genuine alpha transparent background. This is NOT a redesign: preserve the exact sphere's glossy deep midnight navy circular center, vivid icy-blue/white THREE-PRONGED glyph with rays toward upper left, upper right, and downward, its sculpted bevel and lighting, and ONE clean slender luminous warm orange rim. Very thin ivory/silver outer rim and restrained soft blue halo as in the reference. The mark must be absolutely centered and symmetrical, facing straight at camera, no perspective. Single orb only, fills about 88 percent of canvas width, complete uncropped circular silhouette, balanced transparent margins. NO folder shape, NO lettering, NO caption, NO backing tile, NO presentation board, NO phone, NO tiny metal bolts, NO multiple rings, NO orbit debris. This must match the already approved reference sphere as closely as possible and coordinate exactly with the same core in the folder logo. Very clean edges and controlled glow suitable for display at 76 dp in a mobile app. Keep transparent pixels transparent, no opaque white or black square backdrop. Only one sphere asset.

## Verifica

La suite UI apre le schermate reali, prova il pulsante Chiedi a Jarvis e conserva la verifica con caratteri di sistema al 150%. Il rapporto dell'emulatore comprende Home, chat, icona adattiva renderizzata da Android e variante monocromatica a tema. Il test del modello Gemma richiede il modello installato e viene saltato quando manca; la modifica grafica non cambia il motore AI.


## Correzione contatore e dimensione sfera

Il contorno esterno e il contatore interno della lettera a sono parte dello stesso tracciato vettoriale, con riempimento evenOdd: il foro è realmente trasparente e segue lo sfondo. Un test sui pixel del VectorDrawable Android controlla la trasparenza del foro e l'opacità della lettera. La sfera della Home passa da 76 dp a 112 dp sui display da almeno 360 dp, oppure 96 dp su quelli più stretti; anche l'area del pulsante cresce per evitare collisioni con il marchio.
