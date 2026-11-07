# Runs the whole 013b pay-run chain with its test scripts against http://localhost:8080 (Postman CLI).
# MONTH=2026-07 bash postman/run-013b-chain.sh   -- the month must have ended, have locked attendance and no live primary run.
# It leaves an APPROVED run with a recorded payment for that month.
cd "$(dirname "$0")/.."
R="013b Payroll Run"
args=()
add(){ args+=(-i "$1"); }
add "001 Identity & Access/Password Login"
for x in "Runs/List Runs" "Runs/Start Run" "Runs/Get Run" "Lines/List Lines" "Lines/Get Line" "Lines/List Owed"  "Payslips and export/Download Payslip" "Payslips and export/Export CSV" "Payment/Record Payment"  "Adjustments/Record Adjustment" "Adjustments/List Adjustments" "Adjustments/Withdraw Adjustment"  "Adjustments/Record Adjustment" "Runs/Compute Again" "Runs/Get Run" "Lines/Get Line"  "Runs/Submit For Approval" "Adjustments/Withdraw Adjustment" "Runs/Cancel Run" "Payslips and export/Download Payslip"  "Runs/Sign In As Director" "Runs/Return To Draft" "Runs/Get Run" "@login" "Runs/Submit For Approval"  "Payslips and export/Download All Payslips" "Runs/Sign In As Director" "Runs/Approve Run"  "Runs/Get Run" "Lines/Get Line" "Payment/Get Payment" "Payment/Get Person History" "Payslips and export/Download Payslip" "Payslips and export/Download All Payslips" "Payslips and export/Export CSV"  "@login" "Payment/Record Payment" "Payment/Get Payment" "Payment/Get Person History"  "Adjustments/List Adjustments" "Runs/Cancel Run" "Runs/Get Run"; do if [ "$x" = "@login" ]; then add "001 Identity & Access/Password Login"; else add "$R/$x"; fi; done
postman collection run "postman/HLS API" -e "postman/HLS - Local.environment.yaml" --env-var month=${MONTH:-2026-07} "${args[@]}"
