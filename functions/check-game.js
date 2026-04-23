const admin = require('firebase-admin');
const projectId = process.argv[2] || 'pouleparty-prod';
admin.initializeApp({ projectId });
const db = admin.firestore();

(async () => {
  const code = 'CKBQVJ';
  // gameCode = first 6 chars uppercase of gameId
  const snap = await db.collection('games').get();
  const matches = [];
  snap.forEach(d => {
    if (d.id.substring(0, 6).toUpperCase() === code) {
      matches.push(d);
    }
  });

  for (const g of matches) {
    const data = g.data();
    console.log('=== GAME', g.id, 'project=' + projectId);
    console.log('  status:', data.status);
    console.log('  gameMode:', data.gameMode);
    console.log('  creatorId:', data.creatorId);
    console.log('  hunterIds:', JSON.stringify(data.hunterIds));
    console.log('  winners:', data.winners?.length);
    console.log('  timing.start:', data.timing?.start?.toDate());
    console.log('  timing.end:', data.timing?.end?.toDate());
    console.log('  timing.headStartMinutes:', data.timing?.headStartMinutes);
    console.log('  powerUps.enabled:', data.powerUps?.enabled);
    console.log('  powerUps.enabledTypes:', data.powerUps?.enabledTypes);
    console.log('  zone.radius:', data.zone?.radius);
    console.log('  zone.shrinkIntervalMinutes:', data.zone?.shrinkIntervalMinutes);
    console.log('  zone.shrinkMetersPerUpdate:', data.zone?.shrinkMetersPerUpdate);
    console.log('  pricing:', JSON.stringify(data.pricing));

    const pu = await db.collection('games').doc(g.id).collection('powerUps').get();
    console.log('  powerUps spawned:', pu.size);
    pu.forEach(p => {
      const pd = p.data();
      console.log('    -', p.id, 'type=' + pd.type, 'collectedBy=' + (pd.collectedBy || 'NULL'), 'lat=' + pd.location?.latitude?.toFixed(5), 'lng=' + pd.location?.longitude?.toFixed(5), 'activatedAt=' + (pd.activatedAt ? 'YES' : 'no'));
    });
  }
  if (matches.length === 0) console.log('NO GAME FOUND with code', code, 'in project', projectId);
})().catch(e => { console.error(e); process.exit(1); });
