// A successful overwrite must never depend on a best-effort backup.
export function preserveImport(storage, visible, destinationSlot, prefix, stamp) {
  const destination=storage.read(destinationSlot);
  if(!destination.ok)throw new Error('Storage cannot be read. Export your current game and retry the import when storage is available.');
  if(!storage.write(`${prefix}.${stamp}.visible`,JSON.stringify(visible)))throw new Error('There is not enough storage to preserve your current game. Export it, free browser storage, then retry. Nothing was imported.');
  if(destination.value!==null && destination.value!==undefined && !storage.write(`${prefix}.${stamp}.destination`,destination.value))throw new Error('The destination game could not be backed up. Free browser storage and retry. Nothing was imported.');
}
