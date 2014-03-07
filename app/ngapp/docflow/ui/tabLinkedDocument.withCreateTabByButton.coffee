#
# This code left in reserve.  It's implements the case when linkedDocument gets created by button on interface.
# Button should be declared as:
#
# actions:
# - createTab1 display:
#     ngdisabled: 'editor.tabs.tab1.visible'
#     script: 'editor.tabs.tab1.create()'

module = angular.module 'docflow.ui.tabLinkedDocument.withCreateByButton', ['docflow.ui.editor', 'docflow.ui.utils', 'docflow.config']

LinkedDocumentTab = null

module.config ['$docflowEditorProvider.withCreateByButton',(($docflowEditorProvider) ->
  $docflowEditorProvider.register 'linkedDocument', (-> return LinkedDocumentTab)
  return)]

module.run(
  ['$docflowEditor', '$docflowUtils', '$docflowConfig',
  (($docflowEditor, $docflowUtils, $docflowConfig) ->

    class LinkedDocumentTab
      constructor: ((editor, tabName, tabConfig) ->
        $docflowEditor.Tab.super(LinkedDocumentTab, @, editor, tabName, tabConfig)
        @template = 'form' # this overrides template for action(...)
        return)
      splitDoc: ((doc) ->
        if @canCreate = angular.isDefined(doc._u?[@name])
          @item = item = doc._u[@name]
          delete doc._u[@name]
        else
          @item = item = doc[@name]
        delete doc[@name]
        @visible = !!item
        @scope.item = @item if @scope
        $docflowUtils.processDocumentBeforeEditing(item)
        return)
      mergeDoc: ((doc) ->
        doc[@name] = $docflowUtils.buildDocumentUpdate(@item)
        return)
      # creates new linked document within tab
      create: (->
        if @item
          throw Error "Linked document was already attached"
        docConfig = $docflowConfig.docs[@docType]
        if not docConfig
          throw Error "Doctype #{docType} not in docflowConfig"
        if not docConfig.$n
          throw Error "Missing $n for docType '#{@docType}' in docflowConfig"
        @item = angular.copy docConfig.$n
        @visible = true
        @modified = true
        @editor.selectTab @name
        return)
      action: ((action, params) ->
        # TODO: Replace double-proto by 'super' on coffee update
        @.__proto__.__proto__.action.call(@, action, params)
        .then((doc) =>
          root = doc.subj
          if angular.isDefined(root._u?[@name])
            if root._u[@name] # udpate only if it's not null
              root._u[@name] = doc
          else
            if root[@name] # udpate only if it's not null
              root[@name] = doc
          delete doc.subj
          @editor.setDoc(root)
          return)
        return)
    return)]
  )

