module = angular.module 'docflow.ui.tabLinkedDocument', [
  'docflow.config'
  'docflow.ui.editor',
  'docflow.ui.utils',
]

LinkedDocumentTab = null

module.config ['$docflowEditorProvider',(($docflowEditorProvider) ->
  $docflowEditorProvider.registerTabTemplate 'linkedDocument', (-> return LinkedDocumentTab)
  return)]

module.run(
  ['$docflowEditor', '$docflowUtils', '$docflowConfig',
  (($docflowEditor, $docflowUtils, $docflowConfig) ->

    class LinkedDocumentTab extends $docflowEditor.Tab
      constructor: ((editor, tabName, tabConfig) ->
        # It's call of parameterized superclass constructure. Coffee 1.7.1 do not support such mechnism over super(...)
        $docflowEditor.Tab.call @, editor, tabName, tabConfig
        @template = 'form' # this overrides template for action(...)
        return)
      splitDoc: ((doc) ->
        @visible = true
        @isNew = false
        # TODO: Hack: Fix Subj scenario on server side
        @id = doc.id
        if @canCreate = canCreate = angular.isDefined(doc._u?[@name])
          @item = doc._u[@name]
          delete doc._u[@name]
        else
          @item = doc[@name]
        if angular.isObject @item
          $docflowUtils.processDocumentBeforeEditing(@item)
        else if canCreate
          @docConfig = docConfig = $docflowConfig.docs[@docType]
          if not docConfig
            throw Error "DocType #{@docType} not in docflowConfig"
          if not docConfig._n
            throw Error "Missing _n for type '#{@docType}' in docflowConfig"
          if @config.options?.showNew
            @item = angular.copy docConfig._n
            @isNew = true
          else
            @visible = false
        else
          @visible = false
        delete doc[@name]
        @scope.item = @item if @scope
        return)
      mergeDoc: ((doc) ->
        doc[@name] = item = $docflowUtils.buildDocumentUpdate(@item)
        # TODO: Hack: Fix Subj scenario on server side
        sid = $docflowUtils.splitFullId @id
        if sid.id
          item.subj = @id
        return)
      action: ((action, params) ->
        # TODO: Replace double-proto by 'super' on coffee update
#        @.__proto__.__proto__.action.call(@, action, params)
        @callParent('action', action, params)
        .then((resp) =>
          root = resp.doc.subj
          if angular.isDefined(root._u?[@name])
            if root._u[@name] # udpate only if it's not null
              root._u[@name] = resp.doc
          else
            if root[@name] # udpate only if it's not null
              root[@name] = resp.doc
          delete resp.doc.subj
          @editor.setDoc(root)
          return)
        return)
      createLinkedDoc: (->
        if @item
          throw Error "Cannot create linked document: Field #{@name} already not null."
        if not @canCreate
          throw Error "Cannot create linked document: User has no right to modify field #{@name}."
        @item = angular.copy @docConfig._n
        @isNew = true
        @visible = true
        @modified = true
        @editor.selectTab @name
        return)
    return)]
  )

