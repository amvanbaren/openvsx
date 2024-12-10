/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import React, { FunctionComponent, PropsWithChildren, useContext } from 'react';
import { Typography, MenuItem, Link, Button } from '@mui/material';
import React, { FunctionComponent, PropsWithChildren, useContext } from 'react';
import { Typography, MenuItem, Link, Button, IconButton, Accordion, AccordionSummary, Avatar, AccordionDetails } from '@mui/material';
import { useLocation } from 'react-router-dom';
import { Link as RouteLink } from 'react-router-dom';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import GitHubIcon from '@mui/icons-material/GitHub';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import ForumIcon from '@mui/icons-material/Forum';
import InfoIcon from '@mui/icons-material/Info';
import PublishIcon from '@mui/icons-material/Publish';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import { UserAvatar } from '../pages/user/avatar';
import { UserSettingsRoutes } from '../pages/user/user-settings';
import { styled, Theme } from '@mui/material/styles';
import { MainContext } from '../context';
import SettingsIcon from '@mui/icons-material/Settings';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import LogoutIcon from '@mui/icons-material/Logout';
import { AdminDashboardRoutes } from '../pages/admin-dashboard/admin-dashboard';
import { LogoutForm } from '../pages/user/logout';
import { MainContext } from '../context';

//-------------------- Mobile View --------------------//

export const MobileMenuItem = styled(MenuItem)({
    cursor: 'auto',
    '&>a': {
        textDecoration: 'none'
    }
});

export const itemIcon = {
    mr: 1,
    width: '16px',
    height: '16px',
};

export const MobileMenuItemText: FunctionComponent<PropsWithChildren> = ({ children }) => {
    return (
        <Typography variant='body2' color='text.primary' sx={{ display: 'flex', alignItems: 'center', textTransform: 'none' }}>
            {children}
        </Typography>
    );
};

export const MobileUserAvatar: FunctionComponent = () => {
    const context = useContext(MainContext);
    const user = context.user;
    if (!user) {
        return null;
    }

    return <Accordion sx={{ border: 0, borderRadius: 0, boxShadow: '0 0', background: 'transparent' }}>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon />}
            aria-controls='user-actions'
            id='user-avatar'
        >
            <MobileMenuItemText>
                <Avatar
                    src={user.avatarUrl}
                    alt={user.loginName}
                    variant='rounded'
                    sx={itemIcon} />
                {user.loginName}
            </MobileMenuItemText>
        </AccordionSummary>
        <AccordionDetails>
            <MobileMenuItem>
                <Link href={user.homepage}>
                    <MobileMenuItemText>
                        <GitHubIcon sx={itemIcon} />
                        {user.loginName}
                    </MobileMenuItemText>
                </Link>
            </MobileMenuItem>
            <MobileMenuItem>
                <RouteLink to={UserSettingsRoutes.PROFILE}>
                    <MobileMenuItemText>
                        <SettingsIcon sx={itemIcon} />
                        Settings
                    </MobileMenuItemText>
                </RouteLink>
            </MobileMenuItem>
            {
                user.role === 'admin'
                    ? <MobileMenuItem>
                        <RouteLink to={AdminDashboardRoutes.MAIN}>
                            <MobileMenuItemText>
                                <AdminPanelSettingsIcon sx={itemIcon} />
                                Admin Dashboard
                            </MobileMenuItemText>
                        </RouteLink>
                    </MobileMenuItem>
                    : null
            }
            <MobileMenuItem>
                <LogoutForm>
                    <MobileMenuItemText>
                        <LogoutIcon sx={itemIcon} />
                        Log Out
                    </MobileMenuItemText>
                </LogoutForm>
            </MobileMenuItem>
        </AccordionDetails>
    </Accordion>;
};

export const MobileMenuContent: FunctionComponent = () => {
    const { canLogin, service, user } = useContext(MainContext);
    const location = useLocation();

    return <>
        {
            canLogin && (
                user
                    ? <MobileUserAvatar/>
                    : <MobileMenuItem>
                        <Link href={service.getLoginUrl()}>
                            <MobileMenuItemText>
                                <AccountBoxIcon sx={itemIcon} />
                                Log In
                            </MobileMenuItemText>
                        </Link>
                    </MobileMenuItem>
            )
        }
        {
            canLogin && !location.pathname.startsWith(UserSettingsRoutes.ROOT)
            ? <MobileMenuItem>
                <RouteLink to='/user-settings/extensions'>
                    <MobileMenuItemText>
                        <PublishIcon sx={itemIcon} />
                        Publish Extension
                    </MobileMenuItemText>
                </RouteLink>
            </MobileMenuItem>
            : null
        }
        <MobileMenuItem>
            <Link target='_blank' href='https://github.com/eclipse/openvsx'>
                <MobileMenuItemText>
                    <GitHubIcon sx={itemIcon} />
                    Source Code
                </MobileMenuItemText>
            </Link>
        </MobileMenuItem>
        <MobileMenuItem>
            <Link href='https://github.com/eclipse/openvsx/wiki'>
                <MobileMenuItemText>
                    <MenuBookIcon sx={itemIcon} />
                    Documentation
                </MobileMenuItemText>
            </Link>
        </MobileMenuItem>
        <MobileMenuItem>
            <Link href='https://gitter.im/eclipse/openvsx'>
                <MobileMenuItemText>
                    <ForumIcon sx={itemIcon} />
                    Community Chat
                </MobileMenuItemText>
            </Link>
        </MobileMenuItem>
        <MobileMenuItem>
            <RouteLink to='/about'>
                <MobileMenuItemText>
                    <InfoIcon sx={itemIcon} />
                    About This Service
                </MobileMenuItemText>
            </RouteLink>
        </MobileMenuItem>
    </>;
};

//-------------------- Default View --------------------//

export const headerItem = ({ theme }: { theme: Theme }) => ({
    margin: theme.spacing(2.5),
    color: theme.palette.text.primary,
    textDecoration: 'none',
    fontSize: '1.1rem',
    fontFamily: theme.typography.fontFamily,
    fontWeight: theme.typography.fontWeightLight,
    letterSpacing: 1,
    '&:hover': {
        color: theme.palette.secondary.main,
        textDecoration: 'none'
    }
});

export const MenuLink = styled(Link)(headerItem);
export const MenuRouteLink = styled(RouteLink)(headerItem);

export const DefaultMenuContent: FunctionComponent = () => {
    const { service, user, canLogin } = useContext(MainContext);
    return <>
        <MenuLink href='https://github.com/eclipse/openvsx/wiki'>
            Documentation
        </MenuLink>
        <MenuLink href='https://gitter.im/eclipse/openvsx'>
            Community
        </MenuLink>
        <MenuRouteLink to='/about'>
            About
        </MenuRouteLink>
        {canLogin && <Button variant='contained' color='secondary' href='/user-settings/extensions' sx={{ mx: 2.5 }}>
            Publish
        </Button>}
        {
            canLogin && (
                user
                    ? <UserAvatar />
                    : <IconButton
                        href={service.getLoginUrl()}
                        title='Log In'
                        aria-label='Log In' >
                        <AccountBoxIcon />
                    </IconButton>
            )
        }
    </>;
};
