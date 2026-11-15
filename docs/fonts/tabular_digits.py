from fontTools.ttLib import TTFont

# Makes the digits 0-9 TABULAR BY DEFAULT: the cmap points each digit at the glyph the "tnum" feature would
# have substituted. Nothing else changes (hyphen, letters, comma, space stay proportional).
f = TTFont('/in.ttf')
gsub = f['GSUB'].table
tnum = {}
for fr in gsub.FeatureList.FeatureRecord:
    if fr.FeatureTag != 'tnum':
        continue
    for li in fr.Feature.LookupListIndex:
        for st in gsub.LookupList.Lookup[li].SubTable:
            st = getattr(st, 'ExtSubTable', st)
            m = getattr(st, 'mapping', None)
            if m:
                tnum.update(m)

digits = range(0x30, 0x3A)
for table in f['cmap'].tables:
    if not table.isUnicode():
        continue
    for cp in digits:
        if cp in table.cmap:
            table.cmap[cp] = tnum.get(table.cmap[cp], table.cmap[cp])

f.save('/out.ttf')

g = TTFont('/out.ttf')
cm = g.getBestCmap()
hm = g['hmtx']
widths = sorted(set(hm[cm[cp]][0] for cp in digits))
info = 'digit widths: ' + str(widths) + ' | hyphen=' + cm[0x2D] + ' H=' + cm[0x48]
info
