/**
 * SmartBill Pro - In-progress Bill Draft
 * Holds the bill-in-progress (header + line items) in sessionStorage so it
 * survives navigation across New Bill -> Scanner -> Add Items -> Billing.
 */

const BillDraft = (() => {
  const KEY = 'sb_bill_draft';

  function _load() {
    const raw = sessionStorage.getItem(KEY);
    return raw ? JSON.parse(raw) : { header: null, items: [], isDraft: false };
  }

  function _save(draft) {
    sessionStorage.setItem(KEY, JSON.stringify(draft));
  }

  function saveHeader(header) {
    const draft = _load();
    draft.header = header;
    _save(draft);
  }

  function saveAsDraftFlag(flag) {
    const draft = _load();
    draft.isDraft = flag;
    _save(draft);
  }

  function getHeader() {
    return _load().header;
  }

  function getItems() {
    return _load().items;
  }

  /** Adds a scanned/searched product as a line item. Merges quantity if already present. */
  function addItem(item) {
    const draft = _load();
    const existing = draft.items.find(i => i.productId && i.productId === item.productId);
    if (existing) {
      existing.quantity = Number(existing.quantity) + Number(item.quantity);
    } else {
      draft.items.push(item);
    }
    _save(draft);
  }

  function updateItem(index, updates) {
    const draft = _load();
    if (draft.items[index]) {
      Object.assign(draft.items[index], updates);
      _save(draft);
    }
  }

  function removeItem(index) {
    const draft = _load();
    draft.items.splice(index, 1);
    _save(draft);
  }

  function clear() {
    sessionStorage.removeItem(KEY);
  }

  function calculateTotals(discountAmount = 0) {
    const items = getItems();
    let subtotal = 0, gstTotal = 0;
    items.forEach(i => {
      const base = Number(i.quantity) * Number(i.pricePerUnit);
      const gst = base * (Number(i.gstPercentage) / 100);
      subtotal += base;
      gstTotal += gst;
    });
    const grandTotal = subtotal + gstTotal - Number(discountAmount || 0);
    return {
      subtotal: round2(subtotal),
      gstTotal: round2(gstTotal),
      grandTotal: round2(grandTotal)
    };
  }

  function round2(n) {
    return Math.round(n * 100) / 100;
  }

  return {
    saveHeader, getHeader, saveAsDraftFlag,
    addItem, updateItem, removeItem, getItems,
    clear, calculateTotals
  };
})();
