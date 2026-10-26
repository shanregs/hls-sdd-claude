import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { HelperText, RadioButton, Text } from "react-native-paper";
import { listLinkOptions, type CategoryKey, type LinkOption } from "../api/expensesApi";
import { cacheLinkOptions, readListCache } from "../outbox/listCache";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { categoryInfo } from "./categories";
import { BATCH_WHEN_CONNECTED_TEXT } from "./expenseMessages";

export interface LinkChoices {
  status: "none" | "loading" | "ready";
  options: LinkOption[];
  /** True when the options could not be fetched and none were saved: a batch cannot be chosen now. */
  unavailable: boolean;
}

/**
 * The planned work (Travel and Fuel) or induction batches (Training Stay) to offer for a category and date. Fetched online
 * and kept in the encrypted cache; with no connection the last result for the category is used (spec 036 research §9).
 */
export function useLinkChoices(userId: string, category: CategoryKey, date: string | null): LinkChoices {
  const info = categoryInfo(category);
  const wants = info.linkOptional || info.batchRequired;
  const [result, setResult] = useState<{ key: string; options: LinkOption[]; unavailable: boolean } | null>(null);
  const key = `${category}|${date ?? ""}`;

  useEffect(() => {
    if (!wants || !date) return undefined;
    let cancelled = false;
    listLinkOptions(category, date)
      .then((options) => {
        if (cancelled) return;
        setResult({ key, options, unavailable: false });
        void cacheLinkOptions(userId, category, options);
      })
      .catch(async () => {
        const cached = (await readListCache(userId))?.linkOptions[category];
        if (!cancelled) setResult({ key, options: cached ?? [], unavailable: cached === undefined });
      });
    return () => {
      cancelled = true;
    };
  }, [wants, category, date, key, userId]);

  if (!wants) return { status: "none", options: [], unavailable: false };
  if (!result || result.key !== key) return { status: "loading", options: [], unavailable: false };
  return { status: "ready", options: result.options, unavailable: result.unavailable };
}

export interface LinkValue {
  kind: string;
  ref: string;
  label: string;
  date: string | null;
}

interface Props {
  category: CategoryKey;
  choices: LinkChoices;
  value: LinkValue | null;
  onChange: (value: LinkValue | null) => void;
  disabled?: boolean;
}

/** The optional linked work of a Travel and Fuel claim, or the batch a Training Stay claim needs. */
export function LinkField({ category, choices, value, onChange, disabled }: Props) {
  const info = categoryInfo(category);
  if (choices.status === "none") return null;
  const title = info.batchRequired ? "Batch" : "Linked work (optional)";
  return (
    <View style={styles.box}>
      <Text variant="titleSmall" accessibilityRole="header">
        {title}
      </Text>
      {choices.status === "loading" ? <Text variant="bodyMedium">Loading the choices…</Text> : null}
      {choices.status === "ready" && choices.options.length === 0 ? (
        <HelperText type={info.batchRequired ? "error" : "info"} visible>
          {choices.unavailable && info.batchRequired ? BATCH_WHEN_CONNECTED_TEXT : info.batchRequired ? "There is no open batch." : "No planned work around this date."}
        </HelperText>
      ) : null}
      <RadioButton.Group
        value={value?.ref ?? ""}
        onValueChange={(ref) => {
          const option = choices.options.find((o) => o.ref === ref);
          onChange(option ? { kind: option.kind, ref: option.ref, label: option.label, date: option.date } : null);
        }}
      >
        {info.batchRequired ? null : (
          <RadioButton.Item label="None" value="" accessibilityLabel="No linked work" disabled={disabled} style={styles.item} />
        )}
        {choices.options.map((option) => (
          <RadioButton.Item key={option.ref} label={option.label} value={option.ref} accessibilityLabel={option.label} disabled={disabled} style={styles.item} />
        ))}
      </RadioButton.Group>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit / 2 },
  item: { minHeight: minTouchTarget },
});
