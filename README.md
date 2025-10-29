
<h1>Gradle Artisan</h1>
<p>
  A powerful tool window for Android and Gradle developers designed to streamline task management. Gradle Artisan simplifies running, favoriting, and dynamically constructing complex Gradle tasks directly within your IDE, saving you from the command line.
</p>
<br/>
<h2>Features:</h2>
<ul>
  <li>
    <b>Unified Task View:</b> See a comprehensive list of all Gradle tasks from your root project and all submodules in one convenient place. A real-time search bar allows you to filter and find any task instantly.
  </li>
  <br/>
  <li>
    <b>One-Click Execution with Status Feedback:</b> Run any task from the list with a simple right-click. The UI provides immediate visual feedback for each task's status
    The status icon remains until the next refresh, so you always know the result of the last run.
  </li>
  <br/>
  <li>
    <b>Persistent Favorites:</b> Star your most-used tasks for quick access in a dedicated "Favorites" tab. Your favorites are saved per-project and persist across IDE restarts, ensuring your workflow is always ready to go.
  </li>
  <br/>
  <li>
    <b>Powerful Dynamic Task Runner:</b> The core of Gradle Artisan. This feature allows you to build complex, variable-driven task commands without ever leaving your IDE.
    <ul>
      <li><b>Smart Variable Discovery:</b> Automatically discovers project variables defined in your root and app-level <code>build.gradle</code> or <code>build.gradle.kts</code> <code>ext {}</code> blocks using PSI analysis.</li>
      <li><b>Intelligent Autocomplete:</b> Simply type <code>$</code> in the template field to invoke a native-style completion popup, suggesting the discovered variables. It fully supports keyboard navigation.</li>
      <li><b>Dynamic UI:</b> As you type a template (e.g., <code>assemble$TIER$ENV</code>), the UI automatically generates input fields for each variable, pre-filled with default values from your build script.</li>
      <li><b>Flexible Execution:</b> A dedicated checkbox lets you decide whether to run the task with the values currently in the UI fields or to use the absolute latest values from the build file at the moment of execution.</li>
      <li><b>Save & Manage Templates:</b> Save your dynamic task configurations with custom names. Load, update, or delete them from a dedicated list. You can even add a fully constructed dynamic task to your main Favorites list.</li>
    </ul>
  </li>
</ul>
<br/>
<p>
  Gradle Artisan is the perfect companion for developers working on multi-module projects, complex build variant configurations, or anyone who wants to make their Gradle workflow faster and more efficient.
</p>
