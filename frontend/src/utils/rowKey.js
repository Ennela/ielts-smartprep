let counter = 0;

/**
 * A key for a row of an editable list that has no id of its own yet.
 *
 * The admin question editors keyed their rows by array index. Removing a question
 * from the middle then shifted every row up one key, so React reused the inputs of
 * the row that took its place and the text of the deleted question appeared on the
 * next one until the form was reopened.
 */
export const nextRowKey = () => `row-${++counter}`;

/** Gives every item a stable `rowKey`, keeping one it already has. */
export const withRowKeys = (items = []) =>
  items.map((item) => (item.rowKey ? item : { ...item, rowKey: nextRowKey() }));
