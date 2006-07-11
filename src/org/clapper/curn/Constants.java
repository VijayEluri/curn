/*---------------------------------------------------------------------------*\
  $Id$
  ---------------------------------------------------------------------------
  This software is released under a Berkeley-style license:

  Copyright (c) 2004-2006 Brian M. Clapper. All rights reserved.

  Redistribution and use in source and binary forms are permitted provided
  that: (1) source distributions retain this entire copyright notice and
  comment; and (2) modifications made to the software are prominently
  mentioned, and a copy of the original software (or a pointer to its
  location) are included. The name of the author may not be used to endorse
  or promote products derived from this software without specific prior
  written permission.

  THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
  WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.

  Effectively, this means you can do what you want with the software except
  remove this notice or take advantage of the author's name. If you modify
  the software and redistribute your modified version, you must indicate that
  your version is a modification of the original, and you must provide either
  a pointer to or a copy of the original.
\*---------------------------------------------------------------------------*/

package org.clapper.curn;

/**
 * Miscellaneous shared constants that don't logically belong anywhere in
 * particular.
 *
 * @version <tt>$Revision$</tt>
 */
public class Constants
{
    /*----------------------------------------------------------------------*\
                              Constructor
    \*----------------------------------------------------------------------*/
    
    /**
     * Can't be instantiated
     */
    private Constants()
    {
    }

    /*----------------------------------------------------------------------*\
                             Public Constants
    \*----------------------------------------------------------------------*/

    /**
     * Default encoding for "save as" file.
     */
    public static final String DEFAULT_SAVE_AS_ENCODING = "utf-8";

    /**
     * Default resource bundle name for externalized strings
     */
    public static final String BUNDLE_NAME = "org.clapper.curn.Curn";

    /**
     * Environment variable used to find curn home directory.
     */
    public static final String CURN_HOME_ENV_VAR  = "CURN_HOME";

    /**
     * System property used to find curn home directory.
     */
    public static final String CURN_HOME_PROPERTY = "curn.home";
}
