---
title: 'Fan-in to a single required GitHub Action'

tags:
- Programming
---

It doesn't take long for a project to spawn multiple jobs in their GitHub Actions.
Parallelization can lead to huge speedups for PRs.
Job grouping makes it easier to conditionally enable or disable multiple steps.
Each time you add a new job, however, you have to mark it as required in branch protection to prevent failing PRs from accidentally merging.

Being a clever person, you might create a final job which lists all the other jobs as required, and then mark that as the single required job.

```yaml
jobs:
  # …

  final-status:
    needs:
      - build
      - unit-tests
      - emulator-tests
      - screenshot-tests
    # …
```

Unfortunately, this does not work in practice.

GitHub will skip the 'final-status' job if any of its 'needs' fail, and skipped jobs are treated as passing [according to the docs](https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/using-conditions-to-control-job-execution):

> A job that is skipped will report its status as "Success". It will not prevent a pull request from merging, even if it is a required check.

To work around this undesirable behavior, first, change the job to always run (unless canceled):

```diff
   final-status:
+    if: ${{ !cancelled() }}
     needs:
       - build
       - unit-tests
       - emulator-tests
       - screenshot-tests
     …
```

Next, add a step which ensures the status of each 'needs' job was successful:

```yaml
    steps:
      - name: Check
        run: |
          results=$(tr -d '\n' <<< '${{ toJSON(needs.*.result) }}')
          if ! grep -q -v -E '(failure|cancelled)' <<< "$results"; then
            echo "One or more required jobs failed"
            exit 1
          fi
```

Finally, you can mark this job the only required one.
It will now successfully reflect the status of all jobs.
You can also hang additional steps on it, or even entire subsequent jobs (provided they aren't needed for PRs).

I'm using this setup on a few repos [such as Mosaic](https://github.com/JakeWharton/mosaic/blob/54f2183bf8757fe941433c824771272e20d35673/.github/workflows/build.yaml#L299-L321) where you can also see a downstream 'publish' job which only runs on the integration branch.

An alternative is to have a final job which only runs when one of its 'needs' fails and then to fail itself.
[An example of this strategy](https://github.com/actions/runner/issues/2566#issuecomment-1523814835) was posted on the Actions issue tracker.
This approach is simpler, but precludes any additional steps or jobs.
