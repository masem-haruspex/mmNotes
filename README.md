Notes app designed around plain files on disk: notes are folders, content lives in a single text file, and media sits alongside it. Categories are directory nesting (Art → 3D → Inspirations → Character Sculpting). The file layout was chosen deliberately so notes can be synced to a laptop with a shell script and browsed in any file manager. Supports text, audio with transcription, images, and hand-drawn sketches as content blocks. Wrote an adb-based sync tool using staged tarball transfer and atomic rename on-device, so a failed sync can't corrupt the target directory.

<img width="108" height="240" alt="notes2" src="https://github.com/user-attachments/assets/e6fe480b-f171-4776-b2a2-4cb790ce1acd" />
<img width="108" height="240" alt="notes3" src="https://github.com/user-attachments/assets/b7d6441b-df2a-457d-b679-ef6abcb616ef" />
<img width="108" height="240" alt="notes4" src="https://github.com/user-attachments/assets/f44a5741-2a7b-459c-bb24-0ededbdef6a2" />
<img width="108" height="240" alt="notes5" src="https://github.com/user-attachments/assets/935aa941-d52d-4022-bdf3-8edd5698a60e" />
<img width="108" height="240" alt="notes6" src="https://github.com/user-attachments/assets/107587ea-4c23-4ecd-a940-7033a3c5fc6b" />
