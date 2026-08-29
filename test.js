function isDeviceOnline(timestamp) {
  if (!timestamp) return false;
  let timeMs = 0;
  if (timestamp.toDate) {
    timeMs = timestamp.toDate().getTime();
  } else if (typeof timestamp === 'string') {
    timeMs = new Date(timestamp).getTime();
  } else if (timestamp.seconds) {
    timeMs = timestamp.seconds * 1000;
  }
  if (!timeMs) return false;
  return (Date.now() - timeMs) < 2 * 60 * 1000;
}

const mockTimestamp = { toDate: () => new Date() };
console.log("Mock Firestore Timestamp:", isDeviceOnline(mockTimestamp));
console.log("Old timestamp:", isDeviceOnline({ toDate: () => new Date(0) }));
console.log("String:", isDeviceOnline(new Date().toISOString()));
console.log("String old:", isDeviceOnline("1970-01-01T00:00:00Z"));
