# Besom Tutorial

[Please follow the Besom Documentation](https://virtuslab.github.io/besom/docs/tutorial)

## Quick Start

[Follow the instructions](https://www.pulumi.com/docs/clouds/aws/get-started/begin/)
to get started with Pulumi & AWS.

## Deploying and running the program

1. Setup `.bsp` for the IDE:

   ```bash
   scala-cli setup-ide besom
   scala-cli setup-ide lambda
   ```

2. Create a new stack, which is an isolated deployment target for this example:

   ```bash
   pulumi -C besom stack init besom-tutorial-dev
   ```
   
3. Run `pulumi up` to preview and deploy changes. After the preview is shown 
you will be prompted if you want to continue or not.

   ```bash
   pulumi -C besom up
   ```

4. To see the resources that were created, run `pulumi stack output`:

   ```bash
   pulumi -C besom stack output
   ```
5. Open the site URL in a browser to see both the rendered HTML and the favicon:

   ```bash
   open $(pulumi -C besom stack output endpointURL)
   ```

6. From there, feel free to experiment. Simply making edits and running pulumi up will incrementally update your infrastructure.

7. To clean up resources, destroy your stack and remove it:

   ```bash
   pulumi -C besom destroy
   ```
   ```bash
   pulumi -C besom stack rm besom-tutorial-dev
   ```
