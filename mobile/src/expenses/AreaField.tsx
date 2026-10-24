import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { RadioButton, Text } from "react-native-paper";
import { listAreas, type Area } from "../api/expensesApi";
import { readListCache, updateListCache } from "../outbox/listCache";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

const HEAD_OFFICE: Area = { id: null, name: "Head office" };

/** The server's areas (every Zone and Head office), kept in the cache for offline use; Head office alone if neither works. */
export function useAreas(userId: string): Area[] {
  const [areas, setAreas] = useState<Area[]>([HEAD_OFFICE]);
  useEffect(() => {
    let cancelled = false;
    listAreas()
      .then((list) => {
        if (cancelled) return;
        setAreas(list.length > 0 ? list : [HEAD_OFFICE]);
        void updateListCache(userId, { areas: list });
      })
      .catch(async () => {
        const cached = (await readListCache(userId))?.areas;
        if (!cancelled && cached && cached.length > 0) setAreas(cached);
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);
  return areas;
}

interface Props {
  areas: Area[];
  /** The Zone id; null is Head office. */
  value: string | null;
  onChange: (area: Area) => void;
  disabled?: boolean;
}

export function AreaField({ areas, value, onChange, disabled }: Props) {
  return (
    <View style={styles.box}>
      <Text variant="titleSmall" accessibilityRole="header">
        Area
      </Text>
      <RadioButton.Group
        value={value ?? "head-office"}
        onValueChange={(id) => onChange(areas.find((a) => (a.id ?? "head-office") === id) ?? HEAD_OFFICE)}
      >
        {areas.map((area) => (
          <RadioButton.Item
            key={area.id ?? "head-office"}
            label={area.name}
            value={area.id ?? "head-office"}
            accessibilityLabel={`Area ${area.name}`}
            disabled={disabled}
            style={styles.item}
          />
        ))}
      </RadioButton.Group>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit / 2 },
  item: { minHeight: minTouchTarget },
});
