# Cadillac theme assets

- `cadillac-crest.svg`: Cadillac's official monochrome crest, downloaded from
  https://www.cadillac.com/content/dam/cadillac/na/us/english/gmit-assets/primary-navigation-icons/cadillac-2dcrest.svg
- `cadillac-wordmark.svg`: Cadillac script wordmark, credited to Cadillac and sourced
  from its website by the Wikimedia Commons uploader:
  https://commons.wikimedia.org/wiki/File:Cadillac_Wordmark.svg
  Original: https://upload.wikimedia.org/wikipedia/commons/f/fc/Cadillac_Wordmark.svg
  Commons labels this wordmark PD-textlogo.

Downloaded 2026-10-31. Cadillac owns its name and marks.
`convert_vectors.py` preserves the SVG path geometry, replaces the monochrome fill
with silver, and trims the crest's empty vertical margin. The app bundles the
resulting native Android VectorDrawable resources, totaling only a few kilobytes.
The launcher icon places the same crest geometry on a black square background.
The original downloads remain here for provenance and reproducibility.

Dashboard inspiration: Cadillac's dark panoramic infotainment displays and bright
trim, as shown in its official vehicle material:
https://news.cadillac.com/newsroom.detail.print.html/content/Pages/news/us/en/2023/feb/0220-xt4.html

The theme is for the existing DiPlay connection dashboard; the iPhone renders the
CarPlay interface after connection.
