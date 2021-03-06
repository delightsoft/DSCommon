module = angular.module 'docflow.ui.utils', []

module.provider '$docflowUtils', [(->

  utils = @
  @$get = (-> return utils)

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.docType = (docId) ->
    d = docId.indexOf('@')
    return if d > 0 then docId.substr(0, d) else docId

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.splitFullId = ((fullId) ->
    d = fullId.indexOf('@')
    if d > 0
      return {
        docType: fullId.substr(0, d)
        id: fullId.substr(d + 1)}
    return {
      docType: fullId})

  # extracts docType from docId (i.e. 'Order@12' or just 'Order')
  utils.isNewDoc = (docId) ->
    d = docId.indexOf('@')
    return d == -1

  utils.processDocumentsListBeforeEditing = processDocumentsListBeforeEditing = ((list, level) ->
    level = if angular.isDefined(level) then level + 1 else 0
    if list
      i = 0
      for item in list
        processDocumentBeforeEditing item, level
        if level > 0
          item.i = i++
    return)

  utils.processDocumentBeforeEditing = processDocumentBeforeEditing = ((item, level) ->
    return unless item?._u
    level = if angular.isDefined(level) then level + 1 else 0
    for k, v of item._u
      if angular.isArray v
        processDocumentsListBeforeEditing v, level
        item[k] = v
      else if angular.isObject v
        processDocumentBeforeEditing v, level
        item[k] = v
    return)

  utils.buildDocumentsListUpdate = buildDocumentsListUpdate = ((list) ->
    return (buildDocumentUpdate(i) for i in list))

  utils.buildDocumentUpdate = buildDocumentUpdate = ((item) ->
    if not item?._u
      return if item.id then item.id else {}
    for k, v of item
      if k != '_u'
        if item._u.hasOwnProperty(k)
          if angular.isArray v
            item._u[k] = buildDocumentsListUpdate v
          else if angular.isObject v
            item._u[k] = buildDocumentUpdate v
          else if angular.isObject item._u[k]
            item._u[k] = null
    if angular.isDefined item.i
      item._u.i = item.i
    else if angular.isDefined item.id
      item._u.id = item.id
      if angular.isDefined item.rev
        item._u.rev = item.rev
    return item._u)

  utils.encodeUriQuery = ((val, pctEncodeSpaces) ->
    return encodeURIComponent(val).
      replace(/%40/gi, '@').
      replace(/%3A/gi, ':').
      replace(/%24/g, '$').
      replace(/%2C/gi, ',').
      replace(/%20/g, if pctEncodeSpaces then '%20' else '+')
    )

  utils.updateModel = updateModel = ((model, update) ->
    return update unless model
    for k, v of model
      if k.substr(0, 1) != '$'
        # nothing
      else if not update.hasOwnProperty k
        delete model[k]
      else if angular.isArray v # update array
        arr = update[k]
        for u, i in arr
          for m in v
            if m.id == u.id
              updateModel m, u
              arr[i] = m # set model array element to update element
        angular.copy arr, v # copy array from update to model
      else if angular.isObject v # update object
        updateModel(v, update[k])
      else
        model[k] = update[k]
    angular.extend model, update
    return model)

  return)]

module.run(
  ['$rootScope', '$docflowConfig', '$state', '$location', '$sce',
   (($rootScope, $docflowConfig, $state, $location, $sce) ->

     $rootScope.$angular = angular
     $rootScope.$sce = $sce
     $rootScope.pageTitle = ""
     # sets value to right pageTitle from any inner $rootScope
     $rootScope.setPageTitle = ((title) ->
       $rootScope.pageTitle = title
       $state.current?.pageTitle = title
       return)
     return)]
)