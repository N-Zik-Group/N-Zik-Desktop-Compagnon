#!/bin/bash

# Get all workflows
gh api repos/"$GITHUB_REPOSITORY"/actions/runs --paginate > workflows.json


# Filter success workflows
# What workflows eligible to be deleted:
# - Must be "completed"
# - Must be "success"
# - Were updated at least 2 weeks ago
jq -r '
.workflow_runs[] |
select(
  .status == "completed" and
  .conclusion == "success" and
  (now - (.updated_at | fromdate)) >= (14 * 86400)
) |
.id' workflows.json >> success_workflows.txt

# Filter all issue-triggered workflows
# as long as it's not failed.
# What workflows eligible to be deleted:
# - Triggered by an issue
# - Must be "completed"
# - Conclusion is anything but "failure"
jq -r '
.workflow_runs[] |
select(
  .event == "issues" and
  .status == "completed" and
  .conclusion != "failure"
) |
.id' workflows.json >> success_workflows.txt


# Send delete requests
count=0

while IFS= read -r line; do
  ((count++))

  # Adhere to Github's rate limit of 60 requests per hour
  if [ "$count" -gt 60 ]; then
    break
  fi

  gh api -X DELETE "repos/$GITHUB_REPOSITORY/actions/runs/$line"

  # Add some delay so GitHub API doesn't get overloadded
  sleep 1

done < success_workflows.txt
