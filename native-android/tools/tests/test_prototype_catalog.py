import hashlib,json,pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[3]
class AcceptedCatalogTest(unittest.TestCase):
    def test_native_catalog_preserves_accepted_entries_guides_and_image_provenance(self):
        folder=ROOT/'native-android/app/src/main/assets'
        catalog=json.loads((folder/'native-library/catalog.json').read_text(encoding='utf-8'))
        source=(ROOT/'android-app/android/app/src/main/assets/www/index.html').read_bytes().replace(b'\r\n',b'\n')
        self.assertEqual(hashlib.sha256(source).hexdigest(),catalog['sourceHtmlSha256'])
        entries=catalog['entries'];self.assertEqual(23,sum(not row.get('guide',False) for row in entries));self.assertEqual(21,sum(row.get('guide',False) for row in entries))
        self.assertEqual(len(entries),len({row['id'] for row in entries}))
        for entry in entries:
            self.assertTrue(entry['purpose']);self.assertTrue(entry['tech']);self.assertTrue(entry.get('reg'));self.assertTrue(entry.get('prog'))
            image=entry.get('image')
            if not image:continue
            original=ROOT/'android-app/android/app/src/main/assets/www'/image['file']
            data=original.read_bytes()
            if original.suffix=='.svg':data=data.replace(b'\r\n',b'\n')
            self.assertEqual(hashlib.sha256(data).hexdigest(),image['sha256'],entry['id'])
            self.assertTrue((folder/image['nativeFile']).read_bytes().startswith(b'\x89PNG\r\n\x1a\n'))
            self.assertTrue(image['author']);self.assertTrue(image['license']);self.assertTrue(image['fit'][1])
    def test_offline_display_font_matches_its_public_source_blob_and_license(self):
        folder=ROOT/'native-android/app/src/main/assets/licenses'
        metadata=json.loads((folder/'BRICOLAGE-PROVENANCE.json').read_text(encoding='utf-8-sig'))
        font=(ROOT/'native-android/app/src/main/res/font/bricolage_variable.ttf').read_bytes()
        self.assertEqual(hashlib.sha1(b'blob '+str(len(font)).encode()+b'\0'+font).hexdigest(),metadata['gitBlobSha'])
        self.assertIn('OPEN FONT LICENSE',(folder/'BRICOLAGE-OFL.txt').read_text())
