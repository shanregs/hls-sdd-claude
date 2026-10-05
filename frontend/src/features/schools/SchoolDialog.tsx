import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ContactFields, type ContactValue } from "../common/ContactFields";
import {
  getSchoolContacts,
  saveSchoolContacts,
} from "../recruitment/recruitmentApi";
import type { PlaceSummary } from "../zones/zonesApi";
import { PlacePicker } from "./PlacePicker";
import {
  createSchool,
  updateSchool,
  type SchoolProfile,
  type SchoolSummary,
} from "./schoolsApi";

interface SchoolDialogProps {
  /** The School being edited, or undefined to create one. */
  school?: SchoolSummary;
  /** A Manager edits only contact person, phone and address (FR-006); other fields are read-only. */
  limited?: boolean;
  onClose: () => void;
  onSaved: () => void;
}

/** Create or edit a School. The Place (and so the Zone) is chosen only at creation; moving a
 * School uses its own dialog. */
export function SchoolDialog({
  school,
  limited = false,
  onClose,
  onSaved,
}: SchoolDialogProps) {
  const { authFetch } = useAuth();
  const [place, setPlace] = useState<PlaceSummary | null>(null);
  const [placeError, setPlaceError] = useState<string | undefined>();
  const blank: ContactValue = { name: "", phone: "", email: "" };
  const [principal, setPrincipal] = useState<ContactValue>(blank);
  const [accountant, setAccountant] = useState<ContactValue>(blank);

  useEffect(() => {
    if (!school) return;
    void (async () => {
      const result = await getSchoolContacts(authFetch, school.id);
      if (result.ok) {
        setPrincipal(result.data.principal ?? blank);
        setAccountant(result.data.accountant ?? blank);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load once for the School being edited
  }, [authFetch, school?.id]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SchoolProfile>({
    defaultValues: {
      name: school?.name ?? "",
      address: school?.address ?? "",
      contactPerson: school?.contactPerson ?? "",
      contactPhone: school?.contactPhone ?? "",
      billingContact: school?.billingContact ?? "",
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    if (!school && !place) {
      setPlaceError("Choose the place this school is located in.");
      return;
    }
    const result = school
      ? await updateSchool(authFetch, school, values)
      : await createSchool(authFetch, place!.id, values);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    const contactsEntered = [principal, accountant].some(
      (c) => c.name.trim() || c.phone || c.email,
    );
    if (school || contactsEntered) {
      const saved = await saveSchoolContacts(
        authFetch,
        school ? school.id : result.data.id,
        {
          principal: principal.name.trim() ? principal : null,
          accountant: accountant.name.trim() ? accountant : null,
        },
      );
      if (!saved.ok) {
        setError("root", {
          message: `The School was saved but its contacts were not: ${saved.reason}`,
        });
        return;
      }
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{school ? "Edit school" : "Create school"}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errors.root && (
              <Alert severity="error" role="alert">
                {errors.root.message}
              </Alert>
            )}
            <TextField
              label="School name"
              required
              disabled={limited}
              {...register("name", {
                validate: (v) =>
                  v.trim().length > 0 || "School name is required.",
              })}
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
            />
            {!school && (
              <PlacePicker
                value={place}
                error={placeError}
                onChange={(p) => {
                  setPlace(p);
                  setPlaceError(undefined);
                }}
              />
            )}
            <TextField
              label="Address"
              required
              multiline
              minRows={2}
              {...register("address", {
                validate: (v) => v.trim().length > 0 || "Address is required.",
              })}
              error={Boolean(errors.address)}
              helperText={errors.address?.message}
            />
            <TextField label="Contact person" {...register("contactPerson")} />
            <TextField label="Contact phone" {...register("contactPhone")} />
            <TextField
              label="Billing contact"
              disabled={limited}
              {...register("billingContact")}
            />
            <ContactFields
              label="Principal"
              value={principal}
              onChange={setPrincipal}
            />
            <ContactFields
              label="Accountant"
              value={accountant}
              onChange={setAccountant}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {school ? "Save" : "Create"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
